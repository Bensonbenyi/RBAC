package cn.edu.xmu.rbac.service.port;

import java.util.List;

public interface ManagementStore {
  Data.User user(Long id);

  Data.User userByUsername(String username);

  Data.Page<Data.User> users(Long departmentId, int page, int size);

  Data.User createUser(Long departmentId, Data.CreateUser command);

  void updateUser(Long id, Data.Profile command);

  void deleteUser(Long id);

  Data.Department department(Long id);

  Data.Page<Data.Department> departments(int page, int size);

  Data.Department createDepartment(Data.CreateDepartment command);

  void renameDepartment(Long id, String name);

  void deleteDepartment(Long id);

  Data.System system(Long id);

  Data.System systemByKey(String key);

  Data.Page<Data.System> systems(int page, int size);

  Data.System createSystem(Data.CreateSystem command);

  void renameSystem(Long id, String name);

  void deleteSystem(Long id);

  Data.Permission permission(Long id);

  Data.Page<Data.Permission> permissions(Long systemId, int page, int size);

  Data.Permission createPermission(Long systemId, Data.CreatePermission command);

  void renamePermission(Long id, String name);

  void deletePermission(Long id);

  Data.Role role(Long id);

  Data.Page<Data.Role> roles(Long departmentId, int page, int size);

  Data.Role createRole(Long departmentId, String name);

  void renameRole(Long id, String name);

  void deleteRole(Long id);

  List<Data.Role> userRoles(Long userId);

  List<Data.Permission> rolePermissions(Long roleId);

  boolean hasRole(Long userId, Long roleId);

  void assignRole(Long userId, Long roleId);

  void revokeRole(Long userId, Long roleId);

  void grantPermission(Long roleId, Long permissionId);

  void revokePermission(Long roleId, Long permissionId);

  List<String> permissionsForUser(Long userId, String systemKey);

  List<String> rolesForUser(Long userId);
}
