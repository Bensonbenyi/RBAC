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

/** 上层协调员工停用和人工调动，不拥有任何业务模块状态. */
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
    organization.requireEmployeeScope(actorId, employeeId);
    var employee = organization.lockEmployee(employeeId);
    if (employee.version() != version) {
      throw BusinessException.conflict("员工状态已变化");
    }
    organization.setActive(actorId, employeeId, active);
    if (!active) {
      authorization.revokeAll(actorId, employeeId);
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
    if (!organization.administrator(actorId).superAdmin()) {
      throw BusinessException.forbidden();
    }
    organization.lockEmployee(employeeId);
    authorization.requireTransferReady(employeeId);
    organization.transfer(actorId, employeeId, departmentId, version);
    return organization.employee(employeeId);
  }
}
