package cn.edu.xmu.rbac.infrastructure.persistence.adapter;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.DepartmentEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.PermissionEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleInheritanceEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.RolePermissionEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.SystemEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserRoleEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.DepartmentJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.PermissionJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.RoleInheritanceJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.RoleJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.RolePermissionJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.SystemJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.UserJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.UserRoleJpaRepository;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 将管理端口委托给 Spring Data JPA 仓储；在此转换持久化实体和业务 Data. */
@Repository
@Transactional
public class JpaManagementStore implements ManagementStore {
  private final UserJpaRepository users;
  private final DepartmentJpaRepository departments;
  private final SystemJpaRepository systems;
  private final PermissionJpaRepository permissions;
  private final RoleJpaRepository roles;
  private final UserRoleJpaRepository userRoles;
  private final RolePermissionJpaRepository rolePermissions;
  private final RoleInheritanceJpaRepository inheritance;

  /**
   * 注入业务实体和授权关系仓储.
   *
   * @param users 用户 JPA 仓储
   * @param departments 部门 JPA 仓储
   * @param systems 业务系统 JPA 仓储
   * @param permissions 权限 JPA 仓储
   * @param roles 角色 JPA 仓储
   * @param userRoles 用户角色关联的 JPA 仓储
   * @param rolePermissions 角色权限关联的 JPA 仓储
   * @param inheritance 角色继承关联的 JPA 仓储
   */
  public JpaManagementStore(
      UserJpaRepository users,
      DepartmentJpaRepository departments,
      SystemJpaRepository systems,
      PermissionJpaRepository permissions,
      RoleJpaRepository roles,
      UserRoleJpaRepository userRoles,
      RolePermissionJpaRepository rolePermissions,
      RoleInheritanceJpaRepository inheritance) {
    this.users = users;
    this.departments = departments;
    this.systems = systems;
    this.permissions = permissions;
    this.roles = roles;
    this.userRoles = userRoles;
    this.rolePermissions = rolePermissions;
    this.inheritance = inheritance;
  }

  /**
   * 取出仓储查询结果.
   *
   * @param <T> 共享数据项的类型
   * @param value 仓储查询返回的可选记录
   * @return 查询到的持久化记录
   */
  private static <T> T require(Optional<T> value) {
    return value.orElseThrow(BusinessException::missing);
  }

  /**
   * 创建按主键升序排列的分页请求.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return JPA 分页请求
   */
  private static PageRequest request(int page, int size) {
    return PageRequest.of(page, size, Sort.by("id"));
  }

  /**
   * 保留分页元数据并转换为共享分页契约.
   *
   * @param <T> 共享数据项的类型
   * @param result JPA 查询分页结果
   * @return 用例层使用的分页结果
   */
  private static <T> Data.Page<T> page(Page<T> result) {
    return new Data.Page<>(
        result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }

  /**
   * 按编号读取用户.
   *
   * @param id 用户编号
   * @return 目标用户的数据
   */
  @Override
  public Data.User user(Long id) {
    return require(users.findById(id)).toData();
  }

  /**
   * 按用户名查找账号.
   *
   * @param username 唯一登录账号
   * @return 目标用户的数据
   */
  @Override
  public Data.User userByUsername(String username) {
    return require(users.findByUsername(username)).toData();
  }

  /**
   * 分页查询用户.
   *
   * @param departmentId 所属部门编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.User> users(Long departmentId, int page, int size) {
    return page(
        users.findByDepartmentId(departmentId, request(page, size)).map(UserEntity::toData));
  }

  /**
   * 按编号读取部门.
   *
   * @param id 部门编号
   * @return 目标部门的数据
   */
  @Override
  public Data.Department department(Long id) {
    return require(departments.findById(id)).toData();
  }

  /**
   * 分页查询部门.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.Department> departments(int page, int size) {
    return page(departments.findAll(request(page, size)).map(DepartmentEntity::toData));
  }

  /**
   * 分页查询给定父部门的直接子部门.
   *
   * @param parentId 父部门编号，根层使用 null
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size) {
    return page(departments.directory(parentId, keyword, PageRequest.of(page, size)));
  }

  /**
   * 分页查询给定部门的直接成员.
   *
   * @param departmentId 所属部门编号
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size) {
    return page(users.directory(departmentId, keyword, PageRequest.of(page, size)));
  }

  /**
   * 分页查询业务系统.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.System> systems(int page, int size) {
    return page(systems.findAll(request(page, size)).map(SystemEntity::toData));
  }

  /**
   * 按编号读取权限.
   *
   * @param id 权限编号
   * @return 目标权限的数据
   */
  @Override
  public Data.Permission permission(Long id) {
    return require(permissions.findById(id)).toData();
  }

  /**
   * 查询权限.
   *
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.Permission> permissions(Long systemId, int page, int size) {
    require(systems.findById(systemId));
    return page(
        permissions.findBySystemId(systemId, request(page, size)).map(PermissionEntity::toData));
  }

  /**
   * 按编号读取角色.
   *
   * @param id 角色编号
   * @return 目标角色的数据
   */
  @Override
  public Data.Role role(Long id) {
    return require(roles.findById(id)).toData();
  }

  /**
   * 查询已分配角色.
   *
   * @param departmentId 所属部门编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @Override
  public Data.Page<Data.Role> roles(Long departmentId, int page, int size) {
    return page(
        roles.findByDepartmentId(departmentId, request(page, size)).map(RoleEntity::toData));
  }

  /**
   * 保存新建角色.
   *
   * @param departmentId 所属部门编号
   * @param name 角色名称
   * @return 目标角色的数据
   */
  @Override
  public Data.Role createRole(Long departmentId, String name) {
    return roles.save(new RoleEntity(name, departmentId)).toData();
  }

  /**
   * 更新角色名称.
   *
   * @param id 角色编号
   * @param name 角色名称
   */
  @Override
  public void renameRole(Long id, String name) {
    require(roles.findById(id)).rename(name);
  }

  /**
   * 删除指定角色.
   *
   * @param id 角色编号
   */
  @Override
  public void deleteRole(Long id) {
    roles.deleteById(id);
  }

  /**
   * 查询高级角色直接继承的基础角色.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的角色列表
   */
  @Override
  public List<Data.Role> inheritedRoles(Long roleId) {
    return roles.findByIdInOrderById(inheritance.juniorIds(List.of(roleId))).stream()
        .map(RoleEntity::toData)
        .toList();
  }

  /**
   * 写入高级角色到基础角色的直接关联.
   *
   * @param seniorId 高级角色的唯一编号
   * @param juniorId 基础角色的唯一编号
   */
  @Override
  public void inheritRole(Long seniorId, Long juniorId) {
    inheritance.save(new RoleInheritanceEntity(seniorId, juniorId));
  }

  /**
   * 删除指定的直接继承关联.
   *
   * @param seniorId 高级角色的唯一编号
   * @param juniorId 基础角色的唯一编号
   */
  @Override
  public void removeInheritance(Long seniorId, Long juniorId) {
    inheritance.remove(new RoleInheritanceEntity.Key(seniorId, juniorId));
  }

  /**
   * 读取用户直接获得的角色.
   *
   * @param userId 用户的唯一编号
   * @return 满足查询条件的角色列表
   */
  @Override
  public List<Data.Role> userRoles(Long userId) {
    return roles.findByIdInOrderById(userRoles.roleIds(userId)).stream()
        .map(RoleEntity::toData)
        .toList();
  }

  /**
   * 读取角色直接获得的权限.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的权限列表
   */
  @Override
  public List<Data.Permission> rolePermissions(Long roleId) {
    return permissions.forRoles(List.of(roleId)).stream().map(PermissionEntity::toData).toList();
  }

  /**
   * 保存用户与角色的关联.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  @Override
  public void assignRole(Long userId, Long roleId) {
    userRoles.save(new UserRoleEntity(userId, roleId));
  }

  /**
   * 删除用户与角色的关联.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  @Override
  public void revokeRole(Long userId, Long roleId) {
    userRoles.remove(new UserRoleEntity.Key(userId, roleId));
  }

  /**
   * 保存角色与权限的直接关联.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  @Override
  public void grantPermission(Long roleId, Long permissionId) {
    rolePermissions.save(new RolePermissionEntity(roleId, permissionId));
  }

  /**
   * 删除角色与权限的直接关联.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  @Override
  public void revokePermission(Long roleId, Long permissionId) {
    rolePermissions.remove(new RolePermissionEntity.Key(roleId, permissionId));
  }
}
