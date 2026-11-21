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

  public AccessPolicy(EffectiveAccessStore effectiveAccess) {
    this.effectiveAccess = effectiveAccess;
  }

  /** 要求操作者持有平台目标权限，并实时查询有效授权. */
  public void require(Data.Identity actor, String code) {
    if (!"rbac".equals(actor.systemKey()) || !allowed(actor.userId(), "rbac", code)) {
      throw BusinessException.forbidden();
    }
  }

  public boolean allowed(Long userId, String systemKey, String code) {
    return effectiveAccess.permissionsForUser(userId, systemKey).contains(code);
  }

  /** 要求目标部门与操作者所属部门相同. */
  public void scope(Data.Identity actor, Long departmentId) {
    if (actor.departmentId() == null || !Objects.equals(actor.departmentId(), departmentId)) {
      throw BusinessException.forbidden();
    }
  }
}
