package cn.edu.upms.authorization.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 以员工锁、唯一约束和版本检查实现单角色写入，并从当前事实逐次判断权限. */
@Service
public class AuthorizationService implements AuthorizationApi {
  private final AssignmentRepository assignments;
  private final OrganizationApi organization;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuditApi audit;

  /** 只依赖其它模块的公开事实与能力，禁止跨模块实体访问. */
  public AuthorizationService(
      AssignmentRepository assignments,
      OrganizationApi organization,
      IntegrationApi integration,
      CatalogApi catalog,
      AuditApi audit) {
    this.assignments = assignments;
    this.organization = organization;
    this.integration = integration;
    this.catalog = catalog;
    this.audit = audit;
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
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Decision decide(long employeeId, long systemId, String permissionKey) {
    long started = System.nanoTime();
    audit.requireAvailable();
    Effective facts = readEffective(employeeId, systemId);
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
    organization.requireEmployeeScope(actorId, employeeId);
    // 统一锁顺序：员工、系统、角色。停用和调动同样先取得员工锁。
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
