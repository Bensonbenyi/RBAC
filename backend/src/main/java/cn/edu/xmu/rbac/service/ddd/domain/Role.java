package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.role.RoleHierarchy;
import java.util.Set;

/** 角色领域对象，维护同部门且无环的继承规则. */
public class Role {
  private final Data.Role data;

  public Role(Data.Role data) {
    this.data = data;
  }

  /** 基础角色与当前角色同部门，且不能经继承关系到达当前角色. */
  public void requireBaseRole(Data.Role baseRole, Set<Long> reachableRoles) {
    if (!data.departmentId().equals(baseRole.departmentId())) {
      throw BusinessException.forbidden();
    }
    RoleHierarchy.requireAcyclic(reachableRoles, data.id());
  }
}
