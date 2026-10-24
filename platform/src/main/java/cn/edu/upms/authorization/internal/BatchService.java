package cn.edu.upms.authorization.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.authorization.api.BatchApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 按员工逐项提交的批量授权用例，保存可查询和可重试的任务结果.
 *
 * <p>入口先检查整批范围，避免先给范围内员工授权、再发现其他员工越界。之后每名员工拥有独立事务，单项授权与该项 SUCCESS 状态一起提交，其他项失败不撤销已成功项。
 * 本类整体没有 @Transactional，事务模板清楚划出任务创建、单项执行、失败登记等边界.
 *
 * <p>running 防止同一任务并发执行，requestLocks 防止同一幂等标识并发创建。两者都是单节点协调措施，持久化唯一键仍是最后约束；扩为多实例时需要重新设计执行权.
 */
@Service
public class BatchService implements BatchApi {
  private final BatchRepository jobs;
  private final BatchItemRepository items;
  private final AuthorizationApi authorization;
  private final OrganizationApi organization;
  private final AuditApi audit;
  private final TransactionTemplate transaction;
  // Set.add 的原子结果表示能否取得执行权；无论成功还是异常都必须在 finally 释放。
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  // 固定条带避免为每个请求无限创建锁。不同请求哈希碰撞只会多等一会，不会混用结果。
  private final ReentrantLock[] requestLocks = new ReentrantLock[256];

  /** 注入本模块任务存储与公开授权、管理范围能力. */
  public BatchService(
      BatchRepository jobs,
      BatchItemRepository items,
      AuthorizationApi authorization,
      OrganizationApi organization,
      AuditApi audit,
      TransactionTemplate transaction) {
    this.jobs = jobs;
    this.items = items;
    this.authorization = authorization;
    this.organization = organization;
    this.audit = audit;
    this.transaction = transaction;
    for (int i = 0; i < requestLocks.length; i++) {
      requestLocks[i] = new ReentrantLock();
    }
  }

  @Override
  public Job submit(long actorId, Request request) {
    if (request == null || request.requestKey() == null) {
      throw new BusinessException(400, "INVALID_BATCH", "缺少批量请求标识");
    }
    // 同一幂等标识的并发提交在单节点排队，第二次读取已保存结果，而非碰唯一键报错。
    var lock =
        requestLocks[
            Math.floorMod((actorId + ":" + request.requestKey()).hashCode(), requestLocks.length)];
    lock.lock();
    try {
      return submitOwned(actorId, request);
    } finally {
      lock.unlock();
    }
  }

  /** 已取得幂等条带锁后，核对重报内容，再创建并同步执行任务. */
  private Job submitOwned(long actorId, Request request) {
    validate(actorId, request.targets());
    if (!request.requestKey().matches("[A-Za-z0-9-]{8,80}")) {
      throw new BusinessException(400, "INVALID_BATCH", "批量请求标识格式错误");
    }
    // 重报要比较系统、角色、员工集合及版本。仅凭 key 相同就返回，会掩盖用户提交的新意图。
    var existing = jobs.findByActorIdAndRequestKey(actorId, request.requestKey());
    if (existing.isPresent()) {
      Job job = view(existing.get());
      if (job.systemId() != request.systemId()
          || !Objects.equals(job.roleId(), request.roleId())
          || !job.items().stream()
              .map(i -> new Target(i.employeeId(), i.expectedVersion()))
              .collect(Collectors.toSet())
              .equals(Set.copyOf(request.targets()))) {
        throw BusinessException.conflict("同一批量标识不能提交不同内容");
      }
      requireJob(actorId, job.id());
      return job;
    }
    long id =
        transaction.execute(
            status -> {
              // 事务内再次核验，避免预检后发生员工调动或管理员取消。
              validate(actorId, request.targets());
              BatchEntity job = new BatchEntity();
              job.actorId = actorId;
              job.requestKey = request.requestKey();
              job.systemId = request.systemId();
              job.roleId = request.roleId();
              job.revokeRoles = request.roleId() == null;
              job.status = "RUNNING";
              job.createdAt = Instant.now();
              jobs.saveAndFlush(job);
              for (Target target : request.targets()) {
                BatchItemEntity item = new BatchItemEntity();
                item.jobId = job.getId();
                item.employeeId = target.employeeId();
                item.expectedVersion = target.expectedVersion();
                item.status = "PENDING";
                items.save(item);
              }
              audit.record(
                  new AuditApi.Event(
                      actorId,
                      organization.administrator(actorId).departmentId(),
                      null,
                      request.systemId(),
                      "BATCH_CREATED",
                      "batch:" + job.getId(),
                      "SUCCESS",
                      "范围校验后建立 " + request.targets().size() + " 项任务"));
              return job.getId();
            });
    execute(actorId, id);
    return requireJob(actorId, id);
  }

  @Override
  public List<Job> jobs(long actorId) {
    var actor = organization.administrator(actorId);
    return jobs.findAll().stream()
        .filter(j -> actor.superAdmin() || j.actorId == actorId)
        .map(this::view)
        .filter(
            j ->
                actor.superAdmin()
                    || j.items().stream()
                        .allMatch(
                            i ->
                                Objects.equals(
                                    organization.employee(i.employeeId()).departmentId(),
                                    actor.departmentId())))
        .toList();
  }

  @Override
  public Job retry(long actorId, long jobId, List<Target> targets) {
    if (!running.add(jobId)) {
      throw BusinessException.conflict("任务正在执行");
    }
    try {
      return retryOwned(actorId, jobId, targets);
    } finally {
      running.remove(jobId);
    }
  }

  private Job retryOwned(long actorId, long jobId, List<Target> targets) {
    Job job = requireJob(actorId, jobId);
    validate(actorId, targets);
    Map<Long, Long> desired =
        targets.stream().collect(Collectors.toMap(Target::employeeId, Target::expectedVersion));
    transaction.executeWithoutResult(
        status -> {
          var records = items.findByJobId(jobId);
          if (!records.stream()
              .map(i -> i.employeeId)
              .collect(Collectors.toSet())
              .containsAll(desired.keySet())) {
            throw BusinessException.conflict("重试包含非任务员工");
          }
          for (BatchItemEntity item : records) {
            if (desired.containsKey(item.employeeId)) {
              if (item.status.equals("SUCCESS") || item.status.equals("PENDING")) {
                throw BusinessException.conflict("只可重试失败或中断的项目");
              }
              item.expectedVersion = desired.get(item.employeeId);
              item.status = "PENDING";
              item.message = "";
            }
          }
          jobs.findById(jobId).orElseThrow().status = "RUNNING";
          audit.record(
              new AuditApi.Event(
                  actorId,
                  organization.administrator(actorId).departmentId(),
                  null,
                  job.systemId(),
                  "BATCH_RETRY",
                  "batch:" + jobId,
                  "SUCCESS",
                  "明确确认新版本后重试"));
        });
    execute(actorId, jobId, true);
    return requireJob(actorId, jobId);
  }

  /** 上次进程留下的待处理项目不猜测成功；恢复后由用户确认并重试. */
  @EventListener(ApplicationReadyEvent.class)
  public void markInterrupted() {
    transaction.executeWithoutResult(
        status -> {
          for (BatchEntity job : jobs.findAll()) {
            if (job.status.equals("RUNNING")) {
              job.status = "INTERRUPTED";
              items.findByJobId(job.getId()).stream()
                  .filter(i -> i.status.equals("PENDING"))
                  .forEach(
                      i -> {
                        i.status = "INTERRUPTED";
                        i.message = "上次执行中断，请刷新当前授权版本后重试";
                      });
            }
          }
        });
  }

  private void execute(long actorId, long jobId) {
    execute(actorId, jobId, false);
  }

  /**
   * 逐项执行 PENDING 项，每项拥有独立事务，并最终汇总任务状态.
   *
   * @param alreadyOwned 重试入口已经持有执行权时为 true，避免重复取得或过早释放执行权
   */
  private void execute(long actorId, long jobId, boolean alreadyOwned) {
    if (!alreadyOwned && !running.add(jobId)) {
      throw BusinessException.conflict("任务正在执行");
    }
    try {
      var job = jobs.findById(jobId).orElseThrow();
      for (var target : items.findByJobId(jobId)) {
        if (!target.status.equals("PENDING")) {
          continue;
        }
        try {
          transaction.executeWithoutResult(
              status -> {
                var item = items.findById(target.getId()).orElseThrow();
                authorization.change(
                    actorId, item.employeeId, job.systemId, job.roleId, item.expectedVersion);
                item.status = "SUCCESS";
                item.message = "已提交，最迟 3 秒影响后续鉴权";
                items.flush();
              });
        } catch (RuntimeException failure) {
          // 授权事务此时已回滚；用新的事务保存失败，不能在回滚中的事务里写"失败结果"。
          // 若连结果存储也失败，则不承诺任务完成；重启检查会将遗留 PENDING 项标为中断。
          transaction.executeWithoutResult(
              status -> {
                var item = items.findById(target.getId()).orElseThrow();
                item.status = "FAILED";
                item.message =
                    failure instanceof BusinessException business
                        ? business.getMessage()
                        : "技术失败，未报告成功；请检查后重试";
              });
        }
      }
      transaction.executeWithoutResult(
          status -> {
            jobs.findById(jobId).orElseThrow().status =
                items.findByJobId(jobId).stream().allMatch(i -> i.status.equals("SUCCESS"))
                    ? "COMPLETED"
                    : "PARTIAL_FAILURE";
          });
    } finally {
      if (!alreadyOwned) {
        running.remove(jobId);
      }
    }
  }

  private Job requireJob(long actorId, long id) {
    var actor = organization.administrator(actorId);
    BatchEntity job = jobs.findById(id).orElseThrow();
    if (!actor.superAdmin() && job.actorId != actorId) {
      throw BusinessException.forbidden();
    }
    Job view = view(job);
    view.items().forEach(i -> organization.requireEmployeeScope(actorId, i.employeeId()));
    return view;
  }

  private void validate(long actorId, List<Target> targets) {
    audit.requireAvailable();
    organization.administrator(actorId);
    if (targets == null
        || targets.isEmpty()
        || targets.size() > 100
        || targets.stream().map(Target::employeeId).distinct().count() != targets.size()) {
      throw new BusinessException(400, "INVALID_BATCH", "每批须为 1 至 100 名不重复员工");
    }
    // 员工范围逐个在服务端确认；逐项真正写入时 change 仍会在锁内再次校验最新状态。
    targets.forEach(t -> organization.requireEmployeeScope(actorId, t.employeeId()));
  }

  private Job view(BatchEntity job) {
    return new Job(
        job.getId(),
        job.requestKey,
        job.actorId,
        job.systemId,
        job.roleId,
        job.status,
        job.createdAt,
        items.findByJobId(job.getId()).stream()
            .map(i -> new Item(i.employeeId, i.expectedVersion, i.status, i.message))
            .toList());
  }
}
