package cn.edu.xmu.rbac.service.security;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 实时 RBAC 检查及本部门范围约束. */
@Component
public class AccessPolicy {
  private final ManagementStore store;

  public AccessPolicy(ManagementStore store) {
    this.store = store;
  }

  /** require 的用例处理. */
  public void require(Data.Identity actor, String code) {
    if (!"rbac".equals(actor.systemKey()) || !allowed(actor.userId(), "rbac", code)) {
      throw BusinessException.forbidden();
    }
  }

  public boolean allowed(Long userId, String systemKey, String code) {
    return store.permissionsForUser(userId, systemKey).contains(code);
  }

  /** scope 的用例处理. */
  public void scope(Data.Identity actor, Long departmentId) {
    if (actor.departmentId() == null || !Objects.equals(actor.departmentId(), departmentId)) {
      throw BusinessException.forbidden();
    }
  }
}
