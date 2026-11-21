package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.Objects;

/** 用户聚合，维护资料并校验可分配角色的部门. */
public class User {
  private final Data.User data;
  private Data.Profile profile;

  public User(Data.User data) {
    this.data = data;
    this.profile = new Data.Profile(data.name(), data.phone());
  }

  public void changeProfile(Data.Profile change) {
    profile = CloneFactory.copyNotNull(profile, change);
  }

  /** 角色必须与用户属于同一部门，关联唯一性由数据库保证. */
  public void requireRoleInDepartment(Data.Role role) {
    if (!Objects.equals(data.departmentId(), role.departmentId())) {
      throw BusinessException.forbidden();
    }
  }

  public Data.Profile profile() {
    return profile;
  }
}
