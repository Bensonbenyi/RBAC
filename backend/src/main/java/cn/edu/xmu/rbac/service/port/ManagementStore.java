package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 业务持久化端口. */
public interface ManagementStore {
  /**
   * 按编号读取用户.
   *
   * @param id 用户编号
   * @return 目标用户的数据
   */
  Data.User user(Long id);

  /**
   * 按用户名查找账号.
   *
   * @param username 唯一登录账号
   * @return 目标用户的数据
   */
  Data.User userByUsername(String username);

  /**
   * 分页查询用户.
   *
   * @param departmentId 所属部门编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.User> users(Long departmentId, int page, int size);

  /**
   * 按编号读取部门.
   *
   * @param id 部门编号
   * @return 目标部门的数据
   */
  Data.Department department(Long id);

  /**
   * 分页查询部门.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Department> departments(int page, int size);

  /**
   * 查询某一层部门，空上级表示根层.
   *
   * @param parentId 父部门编号，根层使用 null
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size);

  /**
   * 查询指定部门下的用户身份.
   *
   * @param departmentId 所属部门编号
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size);

  /**
   * 分页查询业务系统.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.System> systems(int page, int size);

  /**
   * 按编号读取权限.
   *
   * @param id 权限编号
   * @return 目标权限的数据
   */
  Data.Permission permission(Long id);

  /**
   * 查询权限.
   *
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Permission> permissions(Long systemId, int page, int size);

  /**
   * 按编号读取角色.
   *
   * @param id 角色编号
   * @return 目标角色的数据
   */
  Data.Role role(Long id);

  /**
   * 查询已分配角色.
   *
   * @param departmentId 所属部门编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Role> roles(Long departmentId, int page, int size);

  /**
   * 保存新建角色.
   *
   * @param departmentId 所属部门编号
   * @param name 角色名称
   * @return 目标角色的数据
   */
  Data.Role createRole(Long departmentId, String name);

  /**
   * 更新角色名称.
   *
   * @param id 角色编号
   * @param name 角色名称
   */
  void renameRole(Long id, String name);

  /**
   * 删除指定角色.
   *
   * @param id 角色编号
   */
  void deleteRole(Long id);

  /**
   * 查询高级角色直接继承的基础角色.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的角色列表
   */
  List<Data.Role> inheritedRoles(Long roleId);

  /**
   * 写入高级角色到基础角色的直接关联.
   *
   * @param seniorId 高级角色的唯一编号
   * @param juniorId 基础角色的唯一编号
   */
  void inheritRole(Long seniorId, Long juniorId);

  /**
   * 删除指定的直接继承关联.
   *
   * @param seniorId 高级角色的唯一编号
   * @param juniorId 基础角色的唯一编号
   */
  void removeInheritance(Long seniorId, Long juniorId);

  /**
   * 读取用户直接获得的角色.
   *
   * @param userId 用户的唯一编号
   * @return 满足查询条件的角色列表
   */
  List<Data.Role> userRoles(Long userId);

  /**
   * 读取角色直接获得的权限.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的权限列表
   */
  List<Data.Permission> rolePermissions(Long roleId);

  /**
   * 保存用户与角色的关联.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  void assignRole(Long userId, Long roleId);

  /**
   * 删除用户与角色的关联.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  void revokeRole(Long userId, Long roleId);

  /**
   * 保存角色与权限的直接关联.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  void grantPermission(Long roleId, Long permissionId);

  /**
   * 删除角色与权限的直接关联.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  void revokePermission(Long roleId, Long permissionId);
}
