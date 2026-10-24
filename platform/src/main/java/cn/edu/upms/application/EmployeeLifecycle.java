package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 协调跨模块员工生命周期，用一个事务组织多个模块公开能力.
 *
 * <p>Controller 调用这里，组织模块保存员工状态，授权模块保存角色分配，身份模块维护内存会话。本类不拥有这些数据，也不绕过模块去操作它们的 Repository.
 *
 * <p>数据库状态与审计可以同事务回滚，内存会话不能跟随数据库回滚，因此会话撤销放在提交后执行。重新启用员工只改状态，不恢复已撤销角色或已经失效的会话.
 */
@Service
public class EmployeeLifecycle {
  private final OrganizationApi organization;
  private final AuthorizationApi authorization;
  private final IdentityApi identity;
  private final AuditApi audit;

  /** 注入模块公开能力，统一定义跨模块提交与提交后行为. */
  public EmployeeLifecycle(
      OrganizationApi organization,
      AuthorizationApi authorization,
      IdentityApi identity,
      AuditApi audit) {
    this.organization = organization;
    this.authorization = authorization;
    this.identity = identity;
    this.audit = audit;
  }

  /** 状态与撤权、审计同事务；仅成功提交后撤销全部统一会话. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Employee state(
      long actorId, long employeeId, boolean active, long version) {
    audit.requireAvailable();
    organization.lockManagement(actorId);
    organization.requireEmployeeScope(actorId, employeeId);
    var employee = organization.lockEmployee(employeeId);
    if (employee.version() != version) {
      throw BusinessException.conflict("员工状态已变化");
    }
    // 1. 在同一事务内更新员工；版本比较阻止旧页面覆盖新状态。
    organization.setActive(actorId, employeeId, active);
    if (!active) {
      // 2. 撤权与状态、审计共同提交；任何模块失败都会让全部数据库修改回滚。
      authorization.revokeAll(actorId, employeeId);
      // 3. 只有提交成功才撤销内存会话；回滚时不会误踢掉原本仍有效的用户。
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              identity.revokeEmployeeSessions(employeeId);
            }
          });
    }
    return organization.employee(employeeId);
  }

  /** 调动要求原部门先手工撤权且越过生效窗口，不继承角色或部门管理员身份. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Employee transfer(
      long actorId, long employeeId, long departmentId, long version) {
    audit.requireAvailable();
    organization.lockManagement(actorId);
    if (!organization.administrator(actorId).superAdmin()) {
      throw BusinessException.forbidden();
    }
    organization.lockEmployee(employeeId);
    // 已失效但尚未手工撤销的分配仍会阻挡调动；等待窗口防止旧部门快照跨到新部门。
    authorization.requireTransferReady(employeeId);
    organization.transfer(actorId, employeeId, departmentId, version);
    return organization.employee(employeeId);
  }
}
