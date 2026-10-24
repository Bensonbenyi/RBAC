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

/** 以员工锁、唯一约束和版本检查实现单角色写入，并从当前事实逐次判断权限. */
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
    long started = System.nanoTime();
    audit.requireAvailable();
    Effective facts = runtimeFacts(employeeId, systemId);
    boolean allowed = permissionKey != null && facts.permissions().contains(permissionKey);
    String reason =
        allowed
            ? "ALLOW"
            : facts.reason().equals("EFFECTIVE") ? "PERMISSION_MISSING" : facts.reason();
    if (!allowed) {
      var employee = organization.employee(employeeId);
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
    return new Decision(allowed, reason);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Assignment change(
      long actorId, long employeeId, long systemId, Long roleId, long expectedVersion) {
    audit.requireAvailable();
    organization.lockManagement(actorId);
    organization.requireEmployeeScope(actorId, employeeId);
    // 统一锁顺序：治理锁、员工、系统、角色。组织变更及管理员取消先取相同治理锁。
    var employee = organization.lockEmployee(employeeId);
    organization.requireEmployeeScope(actorId, employeeId);
    var system = integration.lock(systemId);
    AssignmentEntity entity =
        assignments.findByEmployeeIdAndSystemId(employeeId, systemId).orElse(null);
    long actualVersion = entity == null ? -1 : entity.getVersion();
    if (actualVersion != expectedVersion) {
      throw BusinessException.conflict("角色分配已变化，请刷新确认当前角色后重试");
    }
    if (roleId != null) {
      var role = catalog.lock(roleId);
      if (!employee.active()
          || !system.active()
          || !integration.available(systemId, employee.departmentId())
          || !role.active()
          || role.template()
          || !Objects.equals(role.systemId(), systemId)
          || !Objects.equals(role.departmentId(), employee.departmentId())) {
        throw BusinessException.conflict("员工、系统范围或部门角色状态不允许分配");
      }
      organization.activeDepartment(employee.departmentId());
    }
    if (entity == null) {
      entity = new AssignmentEntity();
      entity.employeeId = employeeId;
      entity.systemId = systemId;
    }
    final Long previous = entity.roleId;
    entity.roleId = roleId;
    entity.revoked = roleId == null;
    entity.changedAt = Instant.now();
    assignments.saveAndFlush(entity);
    afterCommitEvict(employeeId, systemId);
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
    return view(entity);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void revokeAll(long actorId, long employeeId) {
    var employee = organization.lockEmployee(employeeId);
    organization.requireEmployeeScope(actorId, employeeId);
    for (AssignmentEntity entity : assignments.findByEmployeeId(employeeId)) {
      if (!entity.revoked) {
        entity.revoked = true;
        entity.roleId = null;
        entity.changedAt = Instant.now();
        afterCommitEvict(employeeId, entity.systemId);
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
    assignments.flush();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireTransferReady(long employeeId) {
    List<AssignmentEntity> records = assignments.findByEmployeeId(employeeId);
    if (records.stream().anyMatch(a -> !a.revoked)) {
      throw BusinessException.conflict("请先由原部门手工撤销全部系统角色，包括无效角色");
    }
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

  private Effective readEffective(long employeeId, long systemId) {
    var employee = organization.employee(employeeId);
    Assignment assignment = assignment(employeeId, systemId);
    CatalogApi.Role role =
        assignment.revoked() || assignment.roleId() == null
            ? null
            : catalog.role(assignment.roleId());
    boolean departmentActive = false;
    if (employee.departmentId() != null) {
      try {
        organization.activeDepartment(employee.departmentId());
        departmentActive = true;
      } catch (BusinessException disabled) {
        departmentActive = false;
      }
    }
    Set<String> keys =
        role == null
            ? Set.of()
            : catalog.permissions(systemId).stream()
                .filter(p -> p.active() && role.permissions().contains(p.id()))
                .map(CatalogApi.Permission::key)
                .collect(Collectors.toUnmodifiableSet());
    var result =
        EffectivePermissionRule.evaluate(
            new EffectivePermissionRule.Facts(
                employee.active(),
                departmentActive,
                integration.available(systemId, employee.departmentId()),
                !assignment.revoked() && role != null,
                role != null && role.active(),
                role != null
                    && !role.template()
                    && Objects.equals(role.systemId(), systemId)
                    && Objects.equals(role.departmentId(), employee.departmentId()),
                keys));
    return new Effective(assignment, role, result.permissions(), result.reason());
  }

  private Effective runtimeFacts(long employeeId, long systemId) {
    if (!cacheEnabled) {
      return reading.execute(status -> readEffective(employeeId, systemId));
    }
    Key key = new Key(employeeId, systemId);
    Snapshot snapshot = snapshots.getIfPresent(key);
    long now = monotonicClock.getAsLong();
    if (snapshot != null && now < snapshot.deadline()) {
      return snapshot.facts();
    }
    // 从最早读取前计时，读取耗时不额外增加陈旧期限；命中绝不续期。
    long started = now;
    Effective facts = reading.execute(status -> readEffective(employeeId, systemId));
    long deadline = started + Duration.ofSeconds(2).toNanos();
    if (monotonicClock.getAsLong() >= deadline) {
      throw BusinessException.unavailable();
    }
    snapshots.put(key, new Snapshot(facts, deadline));
    return facts;
  }

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
