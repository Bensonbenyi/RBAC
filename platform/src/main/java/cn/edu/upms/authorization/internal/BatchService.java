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
    // 幂等标识是识别同一批意图的必要输入，缺少时在建任务前拒绝。
    if (request == null || request.requestKey() == null) {
      throw new BusinessException(400, "INVALID_BATCH", "缺少批量请求标识");
    }
    // 同一幂等标识的并发提交在单节点排队，第二次读取已保存结果，而非碰唯一键报错。
    var lock =
        requestLocks[
            Math.floorMod((actorId + ":" + request.requestKey()).hashCode(), requestLocks.length)];
    // 同一个操作者加请求标识固定落入同一条带；持锁期间读取或创建原任务。
    lock.lock();
    try {
      return submitOwned(actorId, request);
    } finally {
      // 任何返回或异常都释放条带，避免后续重复请求永久等待。
      lock.unlock();
    }
  }

  /** 已取得幂等条带锁后，核对重报内容，再创建并同步执行任务. */
  private Job submitOwned(long actorId, Request request) {
    // 先验证整批员工范围与数量，任何越界都在建立任务前整批拒绝。
    validate(actorId, request.targets());
    if (!request.requestKey().matches("[A-Za-z0-9-]{8,80}")) {
      throw new BusinessException(400, "INVALID_BATCH", "批量请求标识格式错误");
    }
    // 重报要比较系统、角色、员工集合及版本。仅凭 key 相同就返回，会掩盖用户提交的新意图。
    var existing = jobs.findByActorIdAndRequestKey(actorId, request.requestKey());
    if (existing.isPresent()) {
      // 把原任务及逐项结果读出来，再核对是否确实是相同操作。
      Job job = view(existing.get());
      if (job.systemId() != request.systemId()
          || !Objects.equals(job.roleId(), request.roleId())
          // 员工顺序不影响幂等语义，转为员工与期望版本的集合后比较。
          || !job.items().stream()
              .map(i -> new Target(i.employeeId(), i.expectedVersion()))
              .collect(Collectors.toSet())
              .equals(Set.copyOf(request.targets()))) {
        throw BusinessException.conflict("同一批量标识不能提交不同内容");
      }
      // 重报也要校验当前管理范围；原来可操作不代表员工调动后仍可查看任务。
      requireJob(actorId, job.id());
      return job;
    }
    // 先用一个事务保存任务及所有待处理项；后面的逐项执行在该事务结束后开始。
    long id =
        transaction.execute(
            status -> {
              // 事务内再次核验，避免预检后发生员工调动或管理员取消。
              validate(actorId, request.targets());
              BatchEntity job = new BatchEntity();
              // 保存任务发起人和幂等标识，数据库唯一键按这两个值约束重复任务。
              job.actorId = actorId;
              job.requestKey = request.requestKey();
              // 同一任务共用目标系统和目标角色；空角色表示整批撤权。
              job.systemId = request.systemId();
              job.roleId = request.roleId();
              job.revokeRoles = request.roleId() == null;
              // 先记为运行中，并记录创建时间；进程中断后据此识别未完成任务。
              job.status = "RUNNING";
              job.createdAt = Instant.now();
              // 刷新任务取得数据库编号，后面的员工项使用这个编号关联任务。
              jobs.saveAndFlush(job);
              // 把每位员工单独存为 PENDING 项，保留用户确认的旧分配版本。
              for (Target target : request.targets()) {
                BatchItemEntity item = new BatchItemEntity();
                // 员工项归属当前任务；不能只保存员工编号而丢失本次操作上下文。
                item.jobId = job.getId();
                item.employeeId = target.employeeId();
                // 版本沿用请求，不自动改成数据库当前版本；执行时还要检测并发修改。
                item.expectedVersion = target.expectedVersion();
                item.status = "PENDING";
                items.save(item);
              }
              // 任务创建也需要可追溯，成功事件与任务、员工项共同提交。
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
    // 创建事务成功后同步逐项执行，单项失败不会撤销任务里其他已成功项。
    execute(actorId, id);
    // 最终重新读取持久化结果并确认当前范围，不能凭内存循环结束就报告整批成功。
    return requireJob(actorId, id);
  }

  @Override
  public List<Job> jobs(long actorId) {
    // 查询时重新取得管理员事实，先确认还具备后台资格。
    var actor = organization.administrator(actorId);
    return jobs.findAll().stream()
        // 最高管理员可见全局；部门管理员只读取自己发起的任务。
        .filter(j -> actor.superAdmin() || j.actorId == actorId)
        .map(this::view)
        // 任务员工可能已调动，因此还要检查每一位员工当前仍属于可管理部门。
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
    // 核对任务所有权及现有员工范围，再检查本次指定的重试名单。
    Job job = requireJob(actorId, jobId);
    validate(actorId, targets);
    // 将重试名单按员工编号建立索引，值是用户重新确认的期望版本。
    Map<Long, Long> desired =
        targets.stream().collect(Collectors.toMap(Target::employeeId, Target::expectedVersion));
    transaction.executeWithoutResult(
        status -> {
          // 在一个事务内调整要重试的项；任何非法项都会让本次调整整体回滚。
          var records = items.findByJobId(jobId);
          // 确认重试名单是原任务员工集合的子集，不能借重试插入新员工。
          if (!records.stream()
              .map(i -> i.employeeId)
              .collect(Collectors.toSet())
              .containsAll(desired.keySet())) {
            throw BusinessException.conflict("重试包含非任务员工");
          }
          // 逐个检查被选中的原任务项，未选中的失败项保留原结果。
          for (BatchItemEntity item : records) {
            if (desired.containsKey(item.employeeId)) {
              // 成功项不能再次授权，待处理项也不能同时被另一路重试抢走。
              if (item.status.equals("SUCCESS") || item.status.equals("PENDING")) {
                throw BusinessException.conflict("只可重试失败或中断的项目");
              }
              // 替换为新确认的旧版本并重置状态；历史错误提示随这次重试清空。
              item.expectedVersion = desired.get(item.employeeId);
              item.status = "PENDING";
              item.message = "";
            }
          }
          // 重试名单保存后把总体状态改回运行中，成功审计和这些变化一起提交。
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
    // retry 入口已取得执行权，传 true 避免 execute 再次争抢同一任务。
    execute(actorId, jobId, true);
    return requireJob(actorId, jobId);
  }

  /** 上次进程留下的待处理项目不猜测成功；恢复后由用户确认并重试. */
  @EventListener(ApplicationReadyEvent.class)
  public void markInterrupted() {
    transaction.executeWithoutResult(
        status -> {
          // 启动时遍历遗留任务；已经完成的任务保持原结果。
          for (BatchEntity job : jobs.findAll()) {
            // 上次仍为 RUNNING 的任务无法凭总体状态判断完成程度，先标为中断。
            if (job.status.equals("RUNNING")) {
              job.status = "INTERRUPTED";
              items.findByJobId(job.getId()).stream()
                  // 仅标记尚无结果的待处理项，已成功或已失败的员工记录不被覆盖。
                  .filter(i -> i.status.equals("PENDING"))
                  .forEach(
                      i -> {
                        i.status = "INTERRUPTED";
                        // 提示管理员先确认当前分配版本，再明确发起重试。
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
    // 普通执行需要原子取得任务执行权；重试路径已经持有该权利。
    if (!alreadyOwned && !running.add(jobId)) {
      throw BusinessException.conflict("任务正在执行");
    }
    try {
      // 读取任务共用的系统和角色，随后按已保存员工项执行。
      var job = jobs.findById(jobId).orElseThrow();
      for (var target : items.findByJobId(jobId)) {
        // 跳过成功、失败和中断项；只有本轮明确置为 PENDING 的项参与执行。
        if (!target.status.equals("PENDING")) {
          continue;
        }
        try {
          transaction.executeWithoutResult(
              status -> {
                // 每名员工独立开事务并重读任务项，授权变化与 SUCCESS 结果共同提交。
                var item = items.findById(target.getId()).orElseThrow();
                // change 会在锁内重新检查管理员范围、员工与角色状态，以及期望版本。
                authorization.change(
                    actorId, item.employeeId, job.systemId, job.roleId, item.expectedVersion);
                // 只有授权调用成功后才标记成功；flush 出错仍会回滚本项授权与结果。
                item.status = "SUCCESS";
                item.message = "已提交，最迟 3 秒影响后续鉴权";
                // 尽早暴露任务项持久化失败，防止授权已保存却把结果留成未知。
                items.flush();
              });
        } catch (RuntimeException failure) {
          // 授权事务此时已回滚；用新的事务保存失败，不能在回滚中的事务里写"失败结果"。
          // 若连结果存储也失败，则不承诺任务完成；重启检查会将遗留 PENDING 项标为中断。
          transaction.executeWithoutResult(
              status -> {
                var item = items.findById(target.getId()).orElseThrow();
                // 新的失败登记事务只记录结果，不再次修改员工角色。
                item.status = "FAILED";
                // 已知业务冲突可展示中文提示；未知技术异常使用通用提示，避免泄漏内部堆栈。
                item.message =
                    failure instanceof BusinessException business
                        ? business.getMessage()
                        : "技术失败，未报告成功；请检查后重试";
              });
        }
      }
      // 逐项执行结束后按全部结果汇总；任一项非成功都不能报 COMPLETED。
      transaction.executeWithoutResult(
          status -> {
            jobs.findById(jobId).orElseThrow().status =
                items.findByJobId(jobId).stream().allMatch(i -> i.status.equals("SUCCESS"))
                    ? "COMPLETED"
                    : "PARTIAL_FAILURE";
          });
    } finally {
      // 执行权由取得它的入口释放，重试路径的释放由 retry 的 finally 负责。
      if (!alreadyOwned) {
        running.remove(jobId);
      }
    }
  }

  private Job requireJob(long actorId, long id) {
    var actor = organization.administrator(actorId);
    // 先读取任务归属，部门管理员不能凭猜到任务编号查看其他人任务。
    BatchEntity job = jobs.findById(id).orElseThrow();
    if (!actor.superAdmin() && job.actorId != actorId) {
      throw BusinessException.forbidden();
    }
    // 转换后逐项复核当前员工范围，避免原任务在员工调动后泄漏其他部门资料。
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
