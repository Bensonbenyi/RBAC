package cn.edu.xmu.rbac.service.port;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 合并直接授权和继承授权后的只读查询. */
public interface EffectiveAccessStore {
  /** 批量逐层读取继承关系，返回直接角色及全部可达的基础角色. */
  Set<Long> reachableRoles(Collection<Long> roots);

  List<Data.Permission> effectiveRolePermissions(Long roleId);

  List<String> permissionsForUser(Long userId, String systemKey);

  List<String> rolesForUser(Long userId);
}
