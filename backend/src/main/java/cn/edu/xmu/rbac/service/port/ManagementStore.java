package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 业务持久化端口. */
public interface ManagementStore {
  /** 演示初始化只在用户表为空时执行. */
  boolean hasUsers();

  Data.User user(Long id);

  Data.User userByUsername(String username);

  Data.Page<Data.User> users(Long departmentId, int page, int size);

  Data.User createUser(Long departmentId, Data.CreateUser command);

  void updateUser(Long id, Data.Profile command);

  void deleteUser(Long id);

  Data.Department department(Long id);

  Data.Page<Data.Department> departments(int page, int size);

  /** 查询某一层部门，空上级表示根层. */
  Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size);

  /** 查询指定部门下的用户身份. */
  Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size);

  Data.Department createDepartment(Data.CreateDepartment command);

  void renameDepartment(Long id, String name);

  void deleteDepartment(Long id);

  Data.System system(Long id);

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

  /** 锁定部门行，使同部门新增继承边串行；必须在事务中调用. */
  void lockHierarchy(Long departmentId);

  /** 在 READ COMMITTED 事务中读取当前继承边. */
  List<Data.Inheritance> inheritanceEdges(Long departmentId);

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
