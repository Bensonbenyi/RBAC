package cn.edu.xmu.rbac.service.role;

import cn.edu.xmu.rbac.service.port.BusinessException;
import java.util.Set;

/** 继承边由高级角色指向基础角色. */
public final class RoleHierarchy {
  private RoleHierarchy() {}

  /** 基础角色可达高级角色时，加入反向边会形成环. */
  public static void requireAcyclic(Set<Long> reachableFromJunior, Long seniorId) {
    if (reachableFromJunior.contains(seniorId)) {
      throw new BusinessException(409, "ROLE_CYCLE", "角色继承不能形成环");
    }
  }
}
