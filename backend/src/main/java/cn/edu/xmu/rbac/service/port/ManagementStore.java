package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 业务持久化端口. */
public interface ManagementStore {
  Data.User user(Long id);

  Data.User userByUsername(String username);

  Data.Page<Data.User> users(Long departmentId, int page, int size);

  Data.Department department(Long id);

  Data.Page<Data.Department> departments(int page, int size);

  /** 查询某一层部门，空上级表示根层. */
  Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size);

  /** 查询指定部门下的用户身份. */
  Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size);

  Data.System system(Long id);

  Data.Page<Data.System> systems(int page, int size);

  Data.System createSystem(Data.CreateSystem command);

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

  List<Data.Role> inheritedRoles(Long roleId);

  void inheritRole(Long seniorId, Long juniorId);

  void removeInheritance(Long seniorId, Long juniorId);

  List<Data.Role> userRoles(Long userId);

  List<Data.Permission> rolePermissions(Long roleId);

  void assignRole(Long userId, Long roleId);

  void revokeRole(Long userId, Long roleId);

  void grantPermission(Long roleId, Long permissionId);

  void revokePermission(Long roleId, Long permissionId);
}
