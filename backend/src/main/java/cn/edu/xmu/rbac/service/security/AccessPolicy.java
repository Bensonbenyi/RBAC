package cn.edu.xmu.rbac.service.security;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 实时 RBAC 检查及本部门范围约束. */
@Component
public class AccessPolicy {
  private final EffectiveAccessStore effectiveAccess;

  /**
   * 注入当前模块所需的协作组件.
   *
   * @param effectiveAccess 查询角色继承与有效权限的端口
   */
  public AccessPolicy(EffectiveAccessStore effectiveAccess) {
    this.effectiveAccess = effectiveAccess;
  }

  /**
   * 要求操作者持有平台目标权限，并实时查询有效授权.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param code 当前系统的权限编码
   * @throws BusinessException 目标系统或所需权限不符合用例要求
   */
  public void require(Data.Identity actor, String code) {
    if (!"rbac".equals(actor.systemKey()) || !allowed(actor.userId(), "rbac", code)) {
      throw BusinessException.forbidden();
    }
  }

  /**
   * 检查指定权限是否有效.
   *
   * @param userId 用户的唯一编号
   * @param systemKey 当前业务系统的唯一标识
   * @param code 当前系统的权限编码
   * @return 符合目标系统的许可条件时为 true
   */
  public boolean allowed(Long userId, String systemKey, String code) {
    return effectiveAccess.permissionsForUser(userId, systemKey).contains(code);
  }

  /**
   * 要求目标部门与操作者所属部门相同.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param departmentId 所属部门编号
   * @throws BusinessException 操作者没有所属部门或目标属于其他部门
   */
  public void scope(Data.Identity actor, Long departmentId) {
    if (actor.departmentId() == null || !Objects.equals(actor.departmentId(), departmentId)) {
      throw BusinessException.forbidden();
    }
  }
}
