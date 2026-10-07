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
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 整批先验范围、单项原子提交，失败结果可查询；未完成项重启后明确中断. */
@Service
public class BatchService implements BatchApi {
  private final BatchRepository jobs;
  private final BatchItemRepository items;
  private final AuthorizationApi authorization;
  private final OrganizationApi organization;
  private final AuditApi audit;
  private final TransactionTemplate transaction;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();

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
  }

  @Override
  public Job submit(long actorId, Request request) {
    validate(actorId, request.targets());
    if (request.requestKey() == null || !request.requestKey().matches("[A-Za-z0-9-]{8,80}")) {
      throw new BusinessException(400, "INVALID_BATCH", "批量请求标识格式错误");
    }
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
    Job job = requireJob(actorId, jobId);
    validate(actorId, targets);
    if (running.contains(jobId)) {
      throw BusinessException.conflict("任务正在执行");
    }
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
    execute(actorId, jobId);
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
    if (!running.add(jobId)) {
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
      running.remove(jobId);
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
