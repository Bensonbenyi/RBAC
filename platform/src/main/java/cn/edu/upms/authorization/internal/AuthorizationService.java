package cn.edu.upms.authorization.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 授权用例实现：写入单角色分配，读取有效事实，再调用纯规则判断权限.
 *
 * <p>从 {@code change} 看写路径，从 {@code decide -> runtimeFacts -> readEffective} 看读路径。这里只使用其他模块的
 * api；本模块的 AssignmentEntity 不越过公开接口边界.
 *
 * <p>Spring 默认复用同一个 Service 实例处理多个线程，因此员工、操作者等请求状态都保留在方法局部变量中。共享缓存使用线程安全的
 * Caffeine，且默认关闭；写入正确性由数据库事务、统一锁顺序和版本检查共同保证，不能依赖 Java 对象恰好只被一个线程调用.
 */
@Service
public class AuthorizationService implements AuthorizationApi {
  private final AssignmentRepository assignments;
  private final OrganizationApi organization;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuditApi audit;
  private final boolean cacheEnabled;
  private final java.util.function.LongSupplier monotonicClock;
  private final TransactionTemplate reading;

  private record Key(long employeeId, long systemId) {}

  /** deadline 使用单调时钟的纳秒值，只比较时间间隔，不是可显示的日历时间. */
  private record Snapshot(Effective facts, long deadline) {}

  private final Cache<Key, Snapshot> snapshots =
      Caffeine.newBuilder().maximumSize(20000).expireAfterWrite(Duration.ofSeconds(2)).build();

  /** 只依赖其它模块的公开事实与能力，禁止跨模块实体访问. */
  @org.springframework.beans.factory.annotation.Autowired
  public AuthorizationService(
      AssignmentRepository assignments,
      OrganizationApi organization,
      IntegrationApi integration,
      CatalogApi catalog,
      AuditApi audit,
      PlatformTransactionManager manager,
      @Value("${upms.cache-enabled:false}") boolean cacheEnabled) {
    this(
        assignments,
        organization,
        integration,
        catalog,
        audit,
        manager,
        cacheEnabled,
        System::nanoTime);
  }

  /** 测试可替换单调时钟；实例创建后不再修改时钟或请求身份. */
  AuthorizationService(
      AssignmentRepository assignments,
      OrganizationApi organization,
      IntegrationApi integration,
      CatalogApi catalog,
      AuditApi audit,
      PlatformTransactionManager manager,
      boolean cacheEnabled,
      java.util.function.LongSupplier monotonicClock) {
    this.monotonicClock = monotonicClock;
    this.assignments = assignments;
    this.organization = organization;
    this.integration = integration;
    this.catalog = catalog;
    this.audit = audit;
    this.cacheEnabled = cacheEnabled;
    // decide 没有长事务；显式模板只包住一次事实读取，避免缓存命中也占数据库连接。
    // 同类内部调用不会经过 Spring 事务代理，因此不能只依赖公开 effective 方法上的事务注解。
    reading = new TransactionTemplate(manager);
    reading.setReadOnly(true);
    reading.setIsolationLevelName("ISOLATION_READ_COMMITTED");
  }

  @Override
  @Transactional(readOnly = true)
  public Assignment assignment(long employeeId, long systemId) {
    return assignments
        .findByEmployeeIdAndSystemId(employeeId, systemId)
        .map(AuthorizationService::view)
        .orElse(new Assignment(employeeId, systemId, null, true, -1, null));
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
  public Effective effective(long employeeId, long systemId) {
    return readEffective(employeeId, systemId);
  }

  @Override
  public Decision decide(long employeeId, long systemId, String permissionKey) {
    // 用单调时钟计算本次耗时，统计不受系统校时影响。
    long started = System.nanoTime();
    // 1. 审计故障属于技术不可用，须在普通 ALLOW/DENY 判断之前阻断。
    audit.requireAvailable();
    // 读取员工在目标系统的有效权限事实；默认查库，开启缓存时仍检查绝对期限。
    Effective facts = runtimeFacts(employeeId, systemId);
    // 只有明确存在于有效权限集合中的动作才允许；空标识不产生默认权限。
    boolean allowed = permissionKey != null && facts.permissions().contains(permissionKey);
    // 身份与角色都有效但缺少当前动作时，用 PERMISSION_MISSING 区分具体缺权。
    String reason =
        allowed
            ? "ALLOW"
            : facts.reason().equals("EFFECTIVE") ? "PERMISSION_MISSING" : facts.reason();
    if (!allowed) {
      // 业务拒绝也需留痕，先读取员工部门，确定事件所属管理范围。
      var employee = organization.employee(employeeId);
      // 记录动作标识和拒绝原因；不记录访问令牌、手机号等认证资料。
      audit.record(
          new AuditApi.Event(
              employeeId,
              employee.departmentId(),
              null,
              systemId,
              "AUTHORIZATION_DENIED",
              "permission:" + permissionKey,
              "DENY",
              reason));
    }
    // 允许结果只计数；拒绝须持久化成功，审计故障不得被视为普通允许或拒绝。
    audit.count(allowed ? "ALLOW" : "DENY", System.nanoTime() - started);
    // 返回业务结论；此前的技术异常会向外传播，不会被包装为允许。
    return new Decision(allowed, reason);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Assignment change(
      long actorId, long employeeId, long systemId, Long roleId, long expectedVersion) {
    audit.requireAvailable();
    // 1. 锁内重新确认操作者；前端可见按钮或先前的范围查询都不能替代此检查。
    organization.lockManagement(actorId);
    organization.requireEmployeeScope(actorId, employeeId);
    // 统一锁顺序：治理锁、员工、系统、角色。组织变更及管理员取消先取相同治理锁。
    var employee = organization.lockEmployee(employeeId);
    organization.requireEmployeeScope(actorId, employeeId);
    // 员工锁取得后再锁系统，避免与其他管理写入使用相反锁顺序。
    var system = integration.lock(systemId);
    // 查找唯一的员工与系统分配；没有记录表示首次授权，后面才创建实体。
    AssignmentEntity entity =
        assignments.findByEmployeeIdAndSystemId(employeeId, systemId).orElse(null);
    // 2. 悲观锁让竞争写入排队；expectedVersion 仍用于识别用户基于旧结果发出的请求。
    // 例如两个页面都读到版本 4，先提交者成功后，后提交者必须刷新，不能静默覆盖。
    long actualVersion = entity == null ? -1 : entity.getVersion();
    if (actualVersion != expectedVersion) {
      throw BusinessException.conflict("角色分配已变化，请刷新确认当前角色后重试");
    }
    // 3. 撤权无需有效角色；分配则必须同时满足员工、部门、系统与角色归属规则。
    if (roleId != null) {
      // 读取并锁住最新角色，防止校验后角色被同时停用或改变权限。
      var role = catalog.lock(roleId);
      // 逐项核验可分配条件：启用状态、部门可用范围，以及角色的系统和部门归属。
      if (!employee.active()
          || !system.active()
          || !integration.available(systemId, employee.departmentId())
          || !role.active()
          || role.template()
          || !Objects.equals(role.systemId(), systemId)
          || !Objects.equals(role.departmentId(), employee.departmentId())) {
        throw BusinessException.conflict("员工、系统范围或部门角色状态不允许分配");
      }
      // 员工虽有部门编号，部门本身也必须有效，不能给已停用部门继续授权。
      organization.activeDepartment(employee.departmentId());
    }
    // 首次分配才初始化归属字段；已有分配继续沿用原行及其版本。
    if (entity == null) {
      entity = new AssignmentEntity();
      entity.employeeId = employeeId;
      entity.systemId = systemId;
    }
    // 保留旧角色供审计说明替换前后；新角色直接替换，不与旧权限叠加。
    final Long previous = entity.roleId;
    entity.roleId = roleId;
    // 空角色表示明确撤权；更新时间同时作为后续人工调动的生效窗口起点。
    entity.revoked = roleId == null;
    entity.changedAt = Instant.now();
    // 4. flush 触发 SQL 和 JPA 版本更新，让返回值包含新版本；它还没有提交事务。
    // 后面的审计若失败，这次分配也会回滚。数据库唯一键兜住每员工每系统一条分配。
    assignments.saveAndFlush(entity);
    // 先注册提交回调，只有这次分配真正提交后才清理对应权限快照。
    afterCommitEvict(employeeId, systemId);
    // 成功审计参加同一事务；审计写入失败时，刚才 flush 的分配仍会回滚。
    audit.record(
        new AuditApi.Event(
            actorId,
            employee.departmentId(),
            null,
            systemId,
            roleId == null ? "ROLE_REVOKED" : "ROLE_ASSIGNED",
            "employee:" + employeeId,
            "SUCCESS",
            "原角色=" + previous + "，当前角色=" + roleId + "，明确替换且不叠加"));
    // 把持久化实体转换成公开事实，Controller 不接收可修改的 JPA 对象。
    return view(entity);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void revokeAll(long actorId, long employeeId) {
    // 调用方已开启停用事务；在共同员工锁下重新确认管理范围。
    var employee = organization.lockEmployee(employeeId);
    organization.requireEmployeeScope(actorId, employeeId);
    // 遍历全部系统分配，包含角色已经停用、但还未显式撤销的历史分配。
    for (AssignmentEntity entity : assignments.findByEmployeeId(employeeId)) {
      // 已撤销项跳过，避免重复改变时间和重复记录同一次撤权。
      if (!entity.revoked) {
        entity.revoked = true;
        // 清空实际角色并记录撤销时间，保留分配行及其版本。
        entity.roleId = null;
        entity.changedAt = Instant.now();
        // 每个受影响系统单独登记快照失效，回滚时不会执行。
        afterCommitEvict(employeeId, entity.systemId);
        // 每次实际撤权写一条成功事件，便于查询该员工哪些系统受到停用影响。
        audit.record(
            new AuditApi.Event(
                actorId,
                employee.departmentId(),
                null,
                entity.systemId,
                "ROLE_REVOKED",
                "employee:" + employeeId,
                "SUCCESS",
                "员工停用协调撤权"));
      }
    }
    // 受管理实体的字段变化由 JPA 脏检查写入；flush 暴露错误，最终提交由协调者决定。
    assignments.flush();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireTransferReady(long employeeId) {
    // 调动检查所有系统的真实分配，不只看当前有效权限。
    List<AssignmentEntity> records = assignments.findByEmployeeId(employeeId);
    // 即使角色已停用，只要原分配未手工撤销，就不能跨部门移动。
    if (records.stream().anyMatch(a -> !a.revoked)) {
      throw BusinessException.conflict("请先由原部门手工撤销全部系统角色，包括无效角色");
    }
    // 所有撤权均须越过三秒窗口，避免原部门残留快照与新归属交错。
    if (records.stream()
        .anyMatch(a -> Duration.between(a.changedAt, Instant.now()).toMillis() < 3000)) {
      throw BusinessException.conflict("撤权后请等待 3 秒生效窗口，再进行部门调动");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Effective> employeePermissions(long actorId, long employeeId) {
    organization.requireEmployeeScope(actorId, employeeId);
    return integration.systems(actorId).stream()
        .map(s -> readEffective(employeeId, s.id()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> affected(long actorId, long roleId) {
    var role = catalog.role(roleId);
    organization.requireDepartmentScope(actorId, role.departmentId());
    return assignments.findByRoleIdAndRevokedFalse(roleId).stream().map(a -> a.employeeId).toList();
  }

  /**
   * 将多个模块的当前事实组装成纯规则输入，保持查询有效权限与运行鉴权的语义一致.
   *
   * <p>READ_COMMITTED 允许看到已提交变化，但多次查询不是全库原子快照。不能因一次查询曾经有效，就跳过后续请求的状态校验。角色选中的权限还须与当前有效目录取交集.
   */
  private Effective readEffective(long employeeId, long systemId) {
    // 员工状态和当前部门来自组织模块，不能使用请求自报的归属。
    var employee = organization.employee(employeeId);
    // 分配记录解释曾分配什么；没有记录时，assignment 返回版本 -1 的虚拟撤销事实。
    Assignment assignment = assignment(employeeId, systemId);
    // 只有未撤销且拥有角色编号的分配才读取角色，否则按无角色处理。
    CatalogApi.Role role =
        assignment.revoked() || assignment.roleId() == null
            ? null
            : catalog.role(assignment.roleId());
    // 部门未确认默认无效；不能因为归属为空就放宽为全局权限。
    boolean departmentActive = false;
    if (employee.departmentId() != null) {
      try {
        // 部门存在且启用才把该条件置为 true，部门停用异常在这里转为规则拒绝事实。
        organization.activeDepartment(employee.departmentId());
        departmentActive = true;
      } catch (BusinessException disabled) {
        departmentActive = false;
      }
    }
    // 角色选择与当前有效目录取交集；角色曾选中过的停用权限不会继续进入结果。
    Set<String> keys =
        role == null
            ? Set.of()
            : catalog.permissions(systemId).stream()
                .filter(p -> p.active() && role.permissions().contains(p.id()))
                // 把内部权限编号转换成外部系统提交的稳定动作标识。
                .map(CatalogApi.Permission::key)
                .collect(Collectors.toUnmodifiableSet());
    // 纯规则只接收下面这些事实，不访问数据库，也不依赖当前 HTTP 请求。
    var result =
        EffectivePermissionRule.evaluate(
            new EffectivePermissionRule.Facts(
                // 先描述身份、部门、系统范围三个前置条件。
                employee.active(),
                departmentActive,
                integration.available(systemId, employee.departmentId()),
                // 再描述分配是否存在、角色是否启用；这些条件与前面的范围条件分别判断。
                !assignment.revoked() && role != null,
                role != null && role.active(),
                // 最后核对角色不是全局模板，且属于当前员工部门与目标系统。
                role != null
                    && !role.template()
                    && Objects.equals(role.systemId(), systemId)
                    && Objects.equals(role.departmentId(), employee.departmentId()),
                keys));
    // 同时返回原分配、角色与实际有效集合，让管理界面能解释无效原因。
    return new Effective(assignment, role, result.permissions(), result.reason());
  }

  /**
   * 根据配置读取实时事实或短期快照；缓存只能减少查询，不能改变拒绝规则.
   *
   * <p>Caffeine 的写入到期用于清理内存，自定义 deadline 才把数据库读取耗时算进陈旧预算。命中只读，不续期；耗时过长的加载结果直接拒绝，避免刚写入缓存就已超过期限.
   */
  private Effective runtimeFacts(long employeeId, long systemId) {
    // 默认走无缓存路径，由只读事务覆盖这一轮事实查询。
    if (!cacheEnabled) {
      return reading.execute(status -> readEffective(employeeId, systemId));
    }
    // 快照按员工加系统区分，防止把同一员工在不同业务系统的权限混用。
    Key key = new Key(employeeId, systemId);
    Snapshot snapshot = snapshots.getIfPresent(key);
    // Caffeine 可能尚未回收过期条目，读取者仍须主动核验自己的 deadline。
    long now = monotonicClock.getAsLong();
    if (snapshot != null && now < snapshot.deadline()) {
      // 命中只复用该绝对期限内的结果，不通过重新写入来延长寿命。
      return snapshot.facts();
    }
    // 从最早读取前计时，读取耗时不额外增加陈旧期限；命中绝不续期。
    long started = now;
    Effective facts = reading.execute(status -> readEffective(employeeId, systemId));
    // 两秒预算从查库前计算；查询花掉的时间不能在缓存写入后重新获得。
    long deadline = started + Duration.ofSeconds(2).toNanos();
    // 若加载本身已用完预算，明确报技术不可用，避免返回刚生成就过时的允许结果。
    if (monotonicClock.getAsLong() >= deadline) {
      throw BusinessException.unavailable();
    }
    // 只把仍在期限内的完整事实放入有界缓存，再返回同一份事实。
    snapshots.put(key, new Snapshot(facts, deadline));
    return facts;
  }

  /**
   * 仅在数据库真正提交后清理对应快照.
   *
   * <p>若写入尚未提交就失效，另一线程可能读到旧数据并重新缓存。提交后失效缩短可见延迟，并发加载遗漏失效时仍受绝对期限约束。回滚不执行 afterCommit 回调.
   */
  private void afterCommitEvict(long employeeId, long systemId) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                snapshots.invalidate(new Key(employeeId, systemId));
              }
            });
  }

  private static Assignment view(AssignmentEntity entity) {
    return new Assignment(
        entity.employeeId,
        entity.systemId,
        entity.roleId,
        entity.revoked,
        entity.getVersion(),
        entity.changedAt);
  }
}
