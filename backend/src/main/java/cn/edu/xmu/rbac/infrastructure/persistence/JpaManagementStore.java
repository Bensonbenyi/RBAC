package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
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
  private final LoginSessionJpaRepository sessions;

  /** 注入业务实体、授权关系和会话仓储. */
  public JpaManagementStore(
      UserJpaRepository users,
      DepartmentJpaRepository departments,
      SystemJpaRepository systems,
      PermissionJpaRepository permissions,
      RoleJpaRepository roles,
      UserRoleJpaRepository userRoles,
      RolePermissionJpaRepository rolePermissions,
      RoleInheritanceJpaRepository inheritance,
      LoginSessionJpaRepository sessions) {
    this.users = users;
    this.departments = departments;
    this.systems = systems;
    this.permissions = permissions;
    this.roles = roles;
    this.userRoles = userRoles;
    this.rolePermissions = rolePermissions;
    this.inheritance = inheritance;
    this.sessions = sessions;
  }

  private static <T> T require(Optional<T> value) {
    return value.orElseThrow(BusinessException::missing);
  }

  private static PageRequest request(int page, int size) {
    return PageRequest.of(page, size, Sort.by("id"));
  }

  private static <T> Data.Page<T> page(Page<T> result) {
    return new Data.Page<>(
        result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Override
  public boolean hasUsers() {
    return users.count() != 0;
  }

  @Override
  public Data.User user(Long id) {
    return require(users.findById(id)).toData();
  }

  @Override
  public Data.User userByUsername(String username) {
    return require(users.findByUsername(username)).toData();
  }

  @Override
  public Data.Page<Data.User> users(Long departmentId, int page, int size) {
    return page(
        users.findByDepartmentId(departmentId, request(page, size)).map(UserEntity::toData));
  }

  @Override
  public Data.User createUser(Long departmentId, Data.CreateUser c) {
    return users.save(new UserEntity(c.username(), c.name(), c.phone(), departmentId)).toData();
  }

  @Override
  public void updateUser(Long id, Data.Profile c) {
    UserEntity user = require(users.findById(id));
    user.name = c.name();
    user.phone = c.phone();
  }

  /** 删除账号前清理引用；三步在同一事务内提交. */
  @Override
  public void deleteUser(Long id) {
    sessions.removeByUserId(id);
    userRoles.removeByUserId(id);
    users.deleteById(id);
  }

  @Override
  public Data.Department department(Long id) {
    return require(departments.findById(id)).toData();
  }

  @Override
  public Data.Page<Data.Department> departments(int page, int size) {
    return page(departments.findAll(request(page, size)).map(DepartmentEntity::toData));
  }

  @Override
  public Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size) {
    return page(departments.directory(parentId, keyword, PageRequest.of(page, size)));
  }

  @Override
  public Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size) {
    return page(users.directory(departmentId, keyword, PageRequest.of(page, size)));
  }

  @Override
  public Data.Department createDepartment(Data.CreateDepartment c) {
    return departments.save(new DepartmentEntity(c.name(), c.parentId())).toData();
  }

  @Override
  public void renameDepartment(Long id, String name) {
    require(departments.findById(id)).name = name;
  }

  @Override
  public void deleteDepartment(Long id) {
    departments.deleteById(id);
  }

  @Override
  public Data.System system(Long id) {
    return require(systems.findById(id)).toData();
  }

  @Override
  public Data.Page<Data.System> systems(int page, int size) {
    return page(systems.findAll(request(page, size)).map(SystemEntity::toData));
  }

  @Override
  public Data.System createSystem(Data.CreateSystem c) {
    return systems.save(new SystemEntity(c.systemKey(), c.name())).toData();
  }

  @Override
  public void renameSystem(Long id, String name) {
    require(systems.findById(id)).name = name;
  }

  @Override
  public void deleteSystem(Long id) {
    systems.deleteById(id);
  }

  @Override
  public Data.Permission permission(Long id) {
    return require(permissions.findById(id)).toData();
  }

  @Override
  public Data.Page<Data.Permission> permissions(Long systemId, int page, int size) {
    require(systems.findById(systemId));
    return page(
        permissions.findBySystemId(systemId, request(page, size)).map(PermissionEntity::toData));
  }

  @Override
  public Data.Permission createPermission(Long systemId, Data.CreatePermission c) {
    return permissions.save(new PermissionEntity(systemId, c.code(), c.name())).toData();
  }

  @Override
  public void renamePermission(Long id, String name) {
    require(permissions.findById(id)).name = name;
  }

  @Override
  public void deletePermission(Long id) {
    permissions.deleteById(id);
  }

  @Override
  public Data.Role role(Long id) {
    return require(roles.findById(id)).toData();
  }

  @Override
  public Data.Page<Data.Role> roles(Long departmentId, int page, int size) {
    return page(
        roles.findByDepartmentId(departmentId, request(page, size)).map(RoleEntity::toData));
  }

  @Override
  public Data.Role createRole(Long departmentId, String name) {
    return roles.save(new RoleEntity(name, departmentId)).toData();
  }

  @Override
  public void renameRole(Long id, String name) {
    require(roles.findById(id)).name = name;
  }

  @Override
  public void deleteRole(Long id) {
    roles.deleteById(id);
  }

  @Override
  public void lockHierarchy(Long departmentId) {
    require(departments.findForUpdateById(departmentId));
  }

  @Override
  public List<Data.Inheritance> inheritanceEdges(Long departmentId) {
    return inheritance.edgesInDepartment(departmentId);
  }

  @Override
  public List<Data.Role> inheritedRoles(Long roleId) {
    return roles.findByIdInOrderById(inheritance.juniorIds(List.of(roleId))).stream()
        .map(RoleEntity::toData)
        .toList();
  }

  @Override
  public void inheritRole(Long seniorId, Long juniorId) {
    // 调用方已锁定部门，重复 PUT 合并同一条关联。
    inheritance.save(new RoleInheritanceEntity(seniorId, juniorId));
  }

  @Override
  public void removeInheritance(Long seniorId, Long juniorId) {
    inheritance.remove(new RoleInheritanceEntity.Key(seniorId, juniorId));
  }

  @Override
  public List<Data.Role> userRoles(Long userId) {
    return roles.findByIdInOrderById(userRoles.roleIds(userId)).stream()
        .map(RoleEntity::toData)
        .toList();
  }

  @Override
  public List<Data.Permission> rolePermissions(Long roleId) {
    return permissions.forRoles(List.of(roleId)).stream().map(PermissionEntity::toData).toList();
  }

  @Override
  public void assignRole(Long userId, Long roleId) {
    userRoles.save(new UserRoleEntity(userId, roleId));
  }

  @Override
  public void revokeRole(Long userId, Long roleId) {
    userRoles.remove(new UserRoleEntity.Key(userId, roleId));
  }

  /** 同一角色的重复 PUT 串行合并到同一个主键，数据库约束仍保护引用关系. */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void grantPermission(Long roleId, Long permissionId) {
    require(roles.lockById(roleId));
    rolePermissions.save(new RolePermissionEntity(roleId, permissionId));
  }

  @Override
  public void revokePermission(Long roleId, Long permissionId) {
    rolePermissions.remove(new RolePermissionEntity.Key(roleId, permissionId));
  }
}
