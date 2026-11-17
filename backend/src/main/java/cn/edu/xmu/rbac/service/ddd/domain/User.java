package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 用户聚合，维护资料及本部门角色集合. */
public class User {
  private final Data.User data;
  private Data.Profile profile;
  private final Set<Long> roleIds;

  public User(Data.User data) {
    this(data, List.of());
  }

  /** 装配User所需依赖. */
  public User(Data.User data, List<Data.Role> roles) {
    this.data = data;
    this.profile = new Data.Profile(data.name(), data.phone());
    this.roleIds = new HashSet<>(roles.stream().map(Data.Role::id).toList());
  }

  public void changeProfile(Data.Profile change) {
    profile = CloneFactory.copyNotNull(profile, change);
  }

  /** assignRole 的用例处理. */
  public void assignRole(Data.Role role, Long actorDepartment) {
    if (!Objects.equals(data.departmentId(), role.departmentId())
        || !Objects.equals(actorDepartment, data.departmentId())) {
      throw BusinessException.forbidden();
    }
    if (!roleIds.add(role.id())) {
      throw BusinessException.conflict();
    }
  }

  public void revokeRole(Long roleId) {
    roleIds.remove(roleId);
  }

  public Data.Profile profile() {
    return profile;
  }

  public Set<Long> roleIds() {
    return Set.copyOf(roleIds);
  }
}
