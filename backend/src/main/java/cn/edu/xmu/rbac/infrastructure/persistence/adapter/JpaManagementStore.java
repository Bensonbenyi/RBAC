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

  /** 注入业务实体和授权关系仓储. */
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
    require(permissions.findById(id)).rename(name);
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
    require(roles.findById(id)).rename(name);
  }

  @Override
  public void deleteRole(Long id) {
    roles.deleteById(id);
  }

  @Override
  public List<Data.Role> inheritedRoles(Long roleId) {
    return roles.findByIdInOrderById(inheritance.juniorIds(List.of(roleId))).stream()
        .map(RoleEntity::toData)
        .toList();
  }

  @Override
  public void inheritRole(Long seniorId, Long juniorId) {
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

  @Override
  public void grantPermission(Long roleId, Long permissionId) {
    rolePermissions.save(new RolePermissionEntity(roleId, permissionId));
  }

  @Override
  public void revokePermission(Long roleId, Long permissionId) {
    rolePermissions.remove(new RolePermissionEntity.Key(roleId, permissionId));
  }
}
