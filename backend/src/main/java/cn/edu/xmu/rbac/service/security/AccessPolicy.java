package cn.edu.xmu.rbac.service.security;

import cn.edu.xmu.rbac.service.port.*;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
  private final ManagementStore store;

  public AccessPolicy(ManagementStore store) {
    this.store = store;
  }

  public void require(Data.Identity actor, String code) {
    if (!"rbac".equals(actor.systemKey()) || !allowed(actor.userId(), "rbac", code))
      throw BusinessException.forbidden();
  }

  public boolean allowed(Long userId, String systemKey, String code) {
    return store.permissionsForUser(userId, systemKey).contains(code);
  }

  public void scope(Data.Identity actor, Long departmentId) {
    if (actor.departmentId() == null || !Objects.equals(actor.departmentId(), departmentId))
      throw BusinessException.forbidden();
  }
}
