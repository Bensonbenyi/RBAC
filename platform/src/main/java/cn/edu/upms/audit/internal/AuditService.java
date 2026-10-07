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

/** 同步关键事件并维护故障闸门；共享字段仅存线程安全状态. */
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
    requireAvailable();
    AuditEvent entity = new AuditEvent();
    entity.occurredAt = Instant.now();
    entity.actorId = event.actorId();
    entity.departmentId = event.departmentId();
    entity.previousDepartmentId = event.previousDepartmentId();
    entity.systemId = event.systemId();
    entity.eventType = event.eventType();
    entity.objectRef = event.objectRef();
    entity.result = event.result();
    entity.detail = event.detail();
    try {
      // 立即 flush，使审计表故障在用例返回成功前暴露。
      repository.saveAndFlush(entity);
    } catch (RuntimeException failure) {
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
    failureEpoch.incrementAndGet();
    if (paused.compareAndSet(false, true)) {
      gapStarted = Instant.now();
    }
  }

  /** 以数据库实际写入检验恢复；恢复事件明确说明缺口，绝不虚构补录. */
  @Scheduled(fixedDelay = 1000)
  public void probeStorage() {
    long epoch = failureEpoch.get();
    boolean wasPaused = paused.get();
    try {
      AuditEvent event = new AuditEvent();
      event.occurredAt = Instant.now();
      event.eventType = wasPaused ? "AUDIT_RECOVERED" : "AUDIT_HEALTH";
      event.objectRef = "controlled-service";
      event.result = wasPaused ? "RECOVERED" : "SUCCESS";
      event.detail = wasPaused ? "审计恢复，故障开始 " + gapStarted + "，故障区间记录完整性未知" : "审计存储写入探测";
      repository.saveAndFlush(event);
      if (wasPaused) {
        recover(epoch);
      }
    } catch (RuntimeException failure) {
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
    var records =
        repository.findAll(
            (root, cq, cb) -> {
              ArrayList<Predicate> clauses = new ArrayList<>();
              if (departmentId != null) {
                clauses.add(
                    cb.or(
                        cb.equal(root.get("departmentId"), departmentId),
                        cb.equal(root.get("previousDepartmentId"), departmentId)));
              }
              if (eventType != null && !eventType.isBlank()) {
                clauses.add(cb.equal(root.get("eventType"), eventType));
              }
              if (result != null && !result.isBlank()) {
                clauses.add(cb.equal(root.get("result"), result));
              }
              if (query != null && !query.isBlank()) {
                clauses.add(cb.like(root.get("objectRef"), "%" + query + "%"));
              }
              if (from != null) {
                clauses.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
              }
              if (to != null) {
                clauses.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
              }
              return cb.and(clauses.toArray(Predicate[]::new));
            },
            PageRequest.of(
                Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("id").descending()));
    return new PageResult<>(
        records
            .map(
                e ->
                    new View(
                        e.id,
                        e.occurredAt,
                        e.actorId,
                        e.departmentId,
                        e.previousDepartmentId,
                        e.systemId,
                        e.eventType,
                        e.objectRef,
                        e.result,
                        e.detail))
            .getContent(),
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
