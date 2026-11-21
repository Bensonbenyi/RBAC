package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 合并直接授权和继承授权后的只读查询. */
public interface EffectiveAccessStore {
  List<Data.Permission> effectiveRolePermissions(Long roleId);

  List<String> permissionsForUser(Long userId, String systemKey);

  List<String> rolesForUser(Long userId);
}
