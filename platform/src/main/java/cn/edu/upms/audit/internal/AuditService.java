package cn.edu.upms.audit.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.shared.BusinessException;
import cn.edu.upms.shared.PageResult;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存持久化审计，并维护当前进程的运行计数和故障暂停状态.
 *
 * <p>record 参加管理事务，查询不负责推导操作者范围，入口必须先限制部门。审计存储失败后 paused 阻断新的受控请求；每秒通过真实写入探测恢复，恢复事件说明缺口.
 *
 * <p>这是共享 Service：计数使用 LongAdder，暂停开关使用 AtomicBoolean，跨线程读取的缺口起点使用 volatile。failureEpoch
 * 配合短同步段，防止旧探测覆盖刚发生的新故障。这些内存状态重启会丢失，不能称为持久化消息队列或完整故障补录机制.
 */
@Service
public class AuditService implements AuditApi {
  private final AuditRepository repository;
  private final AtomicBoolean paused = new AtomicBoolean();
  private final LongAdder allowed = new LongAdder();
  private final LongAdder denied = new LongAdder();
  private final LongAdder errors = new LongAdder();
  private final LongAdder elapsed = new LongAdder();
  private volatile Instant gapStarted;
  private final AtomicLong failureEpoch = new AtomicLong();

  public AuditService(AuditRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void record(Event event) {
    // 暂停期间立即拒绝新的审计写入，让调用方业务事务明确失败。
    requireAvailable();
    AuditEvent entity = new AuditEvent();
    // 发生时间取服务器时钟，操作者编号取调用方已验证的身份。
    entity.occurredAt = Instant.now();
    entity.actorId = event.actorId();
    // 新旧部门用于调动历史的双向范围查询，普通事件可不填旧部门。
    entity.departmentId = event.departmentId();
    entity.previousDepartmentId = event.previousDepartmentId();
    // 系统编号和事件类型说明这次变更属于哪个业务范围。
    entity.systemId = event.systemId();
    entity.eventType = event.eventType();
    // 对象引用定位目标，结果描述已发生事件的语义。
    entity.objectRef = event.objectRef();
    entity.result = event.result();
    // 补充便于人工理解的说明；避免在调用方把密钥或令牌放进详情。
    entity.detail = event.detail();
    try {
      // 使用默认 REQUIRED 传播：上层已有事务就加入，否则开启独立事务。
      // flush 不等于 commit；外层后续失败时，已 flush 的成功事件仍随业务整体回滚。
      // 立即 flush，使审计表故障在用例返回成功前暴露。
      repository.saveAndFlush(entity);
    } catch (RuntimeException failure) {
      // 写入异常先标记全局暂停，再向调用方返回不可用，使业务回滚。
      pause();
      throw BusinessException.unavailable();
    }
  }

  @Override
  public void requireAvailable() {
    if (paused.get()) {
      throw BusinessException.unavailable();
    }
  }

  @Override
  public synchronized void pause() {
    // 每次失败都推进代次，即使已经暂停；只记录第一次暂停的时间以描述整个缺口区间。
    failureEpoch.incrementAndGet();
    if (paused.compareAndSet(false, true)) {
      gapStarted = Instant.now();
    }
  }

  /** 以数据库实际写入检验恢复；恢复事件明确说明缺口，绝不虚构补录. */
  @Scheduled(fixedDelay = 1000)
  public void probeStorage() {
    // 先记住本次探测依据的故障代次，再执行 I/O，避免持同步锁等待数据库。
    long epoch = failureEpoch.get();
    // 记住探测起点的暂停状态，用它选择健康事件或恢复事件。
    boolean wasPaused = paused.get();
    try {
      // 探测也通过真实审计表写入，单纯能连上数据库不足以证明可记录事件。
      AuditEvent event = new AuditEvent();
      event.occurredAt = Instant.now();
      // 恢复事件与例行健康事件区分开，便于识别故障区间。
      event.eventType = wasPaused ? "AUDIT_RECOVERED" : "AUDIT_HEALTH";
      event.objectRef = "controlled-service";
      // 恢复结果只说明此刻写入成功，详情仍明确历史缺口完整性未知。
      event.result = wasPaused ? "RECOVERED" : "SUCCESS";
      event.detail = wasPaused ? "审计恢复，故障开始 " + gapStarted + "，故障区间记录完整性未知" : "审计存储写入探测";
      // 这次写入成功后才尝试解除暂停，不能先放行再验证存储。
      repository.saveAndFlush(event);
      // 只有起点已暂停才走恢复判断，代次检查防止覆盖探测期间的新故障。
      if (wasPaused) {
        recover(epoch);
      }
    } catch (RuntimeException failure) {
      // 探测失败推进故障代次，并继续阻止新的受控请求。
      pause();
    }
  }

  private synchronized void recover(long epoch) {
    // 探测期间若又出现写失败，旧探测结果不能解除新的暂停状态。
    if (failureEpoch.get() == epoch) {
      paused.set(false);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<View> query(
      Long departmentId,
      String eventType,
      String result,
      String query,
      Instant from,
      Instant to,
      int page,
      int size) {
    // 入口已计算可信部门范围，这里把范围和用户筛选一并交给数据库。
    var records =
        repository.findAll(
            (root, cq, cb) -> {
              // 逐项组合动态条件，未提供的可选筛选不加入查询。
              ArrayList<Predicate> clauses = new ArrayList<>();
              // 当前部门或原部门任一匹配即可查到调动事件；其余条件仍取 AND。
              if (departmentId != null) {
                clauses.add(
                    cb.or(
                        cb.equal(root.get("departmentId"), departmentId),
                        cb.equal(root.get("previousDepartmentId"), departmentId)));
              }
              // 事件类型按精确值匹配，避免混淆不同管理行为。
              if (eventType != null && !eventType.isBlank()) {
                clauses.add(cb.equal(root.get("eventType"), eventType));
              }
              // 成功、拒绝等结果也独立筛选，可与事件类型组合使用。
              if (result != null && !result.isBlank()) {
                clauses.add(cb.equal(root.get("result"), result));
              }
              // 关键字在对象引用中做 LIKE 查询；此处保留当前实现的通配符语义。
              if (query != null && !query.isBlank()) {
                clauses.add(cb.like(root.get("objectRef"), "%" + query + "%"));
              }
              // 起始时间包含边界，便于按明确时间区间复查事件。
              if (from != null) {
                clauses.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
              }
              // 结束时间同样包含边界，传空表示不限制该端。
              if (to != null) {
                clauses.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
              }
              // 所有已加入条件须同时成立，部门范围不能被关键字筛选放宽。
              return cb.and(clauses.toArray(Predicate[]::new));
            },
            // 限制页码和每页条数，按递增编号倒序先展示较新事件。
            PageRequest.of(
                Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("id").descending()));
    // 把持久化事件转换为只读视图，响应不暴露 JPA 实体。
    return new PageResult<>(
        records
            .map(
                e ->
                    new View(
                        // 保留事件身份和发生时间，便于导出与界面记录互相定位。
                        e.id,
                        e.occurredAt,
                        // 保留操作者及变更后部门；历史编号不会随当前员工资料改写。
                        e.actorId,
                        e.departmentId,
                        // 原部门和目标系统帮助解释事件当时的管理上下文。
                        e.previousDepartmentId,
                        e.systemId,
                        // 类型和对象引用说明发生了哪类操作、影响了谁。
                        e.eventType,
                        e.objectRef,
                        // 结果与详情共同表达事件语义，不把技术失败伪装成成功。
                        e.result,
                        e.detail))
            .getContent(),
        // 总数来自同一组筛选条件，页码与大小沿用请求中的分页信息。
        records.getTotalElements(),
        page,
        size);
  }

  @Override
  public void count(String result, long elapsedNanos) {
    switch (result) {
      case "ALLOW" -> allowed.increment();
      case "DENY" -> denied.increment();
      default -> errors.increment();
    }
    // 这里只累计耗时；平均值可由总耗时与次数推导，但不能用它计算 P95。
    elapsed.add(elapsedNanos);
  }

  @Override
  public Map<String, Object> statistics() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("allowed", allowed.sum());
    values.put("denied", denied.sum());
    values.put("errors", errors.sum());
    values.put("elapsedNanos", elapsed.sum());
    values.put("paused", paused.get());
    values.put("gapStarted", gapStarted == null ? "" : gapStarted.toString());
    return values;
  }
}
