package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.*;
import java.util.*;

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

  public void grantPermission(Data.Permission permission) {
    if (permission == null) throw BusinessException.missing();
    permissionIds.add(permission.id());
  }

  public void revokePermission(Long id) {
    permissionIds.remove(id);
  }

  public Set<Long> permissionIds() {
    return Set.copyOf(permissionIds);
  }
}
