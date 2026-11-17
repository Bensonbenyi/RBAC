package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 角色聚合，维护名称和已授予权限集合. */
public class Role {
  private String name;
  private final Set<Long> permissionIds;

  public Role(Data.Role data) {
    this(data, List.of());
  }

  public Role(Data.Role data, List<Data.Permission> permissions) {
    name = data.name();
    permissionIds = new HashSet<>(permissions.stream().map(Data.Permission::id).toList());
  }

  public void rename(String value) {
    name = value;
  }

  public String name() {
    return name;
  }

  /** grantPermission 的用例处理. */
  public void grantPermission(Data.Permission permission) {
    if (permission == null) {
      throw BusinessException.missing();
    }
    permissionIds.add(permission.id());
  }

  public void revokePermission(Long id) {
    permissionIds.remove(id);
  }

  public Set<Long> permissionIds() {
    return Set.copyOf(permissionIds);
  }
}
