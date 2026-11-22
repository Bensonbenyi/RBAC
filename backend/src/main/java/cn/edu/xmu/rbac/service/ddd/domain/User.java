package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.Objects;

/** 用户聚合，校验可分配角色的部门. */
public class User {
  private final Data.User data;

  public User(Data.User data) {
    this.data = data;
  }

  /** 角色必须与用户属于同一部门，关联唯一性由数据库保证. */
  public void requireRoleInDepartment(Data.Role role) {
    if (!Objects.equals(data.departmentId(), role.departmentId())) {
      throw BusinessException.forbidden();
    }
  }
}
