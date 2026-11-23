package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.Objects;

/** 用户聚合，校验可分配角色的部门. */
public class User {
  private final Data.User data;

  /**
   * 注入用户所需的协作组件.
   *
   * @param data 构造领域对象时使用的共享数据快照
   */
  public User(Data.User data) {
    this.data = data;
  }

  /**
   * 角色必须与用户属于同一部门，关联唯一性由数据库保证.
   *
   * @param role 拟分配的角色
   * @throws BusinessException 角色归属与用户所属部门不同
   */
  public void requireRoleInDepartment(Data.Role role) {
    if (!Objects.equals(data.departmentId(), role.departmentId())) {
      throw BusinessException.forbidden();
    }
  }
}
