package cn.edu.xmu.rbac.infrastructure.persistence.adapter;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.PermissionEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.PermissionJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.RoleInheritanceJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.RoleJpaRepository;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.UserRoleJpaRepository;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 批量读取当前继承边，合并直接角色和可达角色的权限. */
@Repository
@Transactional(readOnly = true)
public class JpaEffectiveAccessStore implements EffectiveAccessStore {
  private final UserRoleJpaRepository userRoles;
  private final RoleInheritanceJpaRepository inheritance;
  private final RoleJpaRepository roles;
  private final PermissionJpaRepository permissions;

  /** 组装直接角色、继承边和权限目录的查询仓储. */
  public JpaEffectiveAccessStore(
      UserRoleJpaRepository userRoles,
      RoleInheritanceJpaRepository inheritance,
      RoleJpaRepository roles,
      PermissionJpaRepository permissions) {
    this.userRoles = userRoles;
    this.inheritance = inheritance;
    this.roles = roles;
    this.permissions = permissions;
  }

  @Override
  public List<Data.Permission> effectiveRolePermissions(Long roleId) {
    return permissions.forRoles(reachableRoles(List.of(roleId))).stream()
        .map(PermissionEntity::toData)
        .toList();
  }

  @Override
  public List<String> permissionsForUser(Long userId, String systemKey) {
    Set<Long> effectiveRoles = reachableRoles(userRoles.roleIds(userId));
    return effectiveRoles.isEmpty()
        ? List.of()
        : permissions.codesForRoles(effectiveRoles, systemKey);
  }

  @Override
  public List<String> rolesForUser(Long userId) {
    return roles.findByIdInOrderById(reachableRoles(userRoles.roleIds(userId))).stream()
        .map(role -> role.toData().name())
        .toList();
  }

  @Override
  public Set<Long> reachableRoles(Collection<Long> roots) {
    Set<Long> reached = new HashSet<>(roots);
    Collection<Long> frontier = roots;
    while (!frontier.isEmpty()) {
      // 一次查询当前一层的全部基础角色；多路径到达的角色只展开一次。
      frontier = inheritance.juniorIds(frontier).stream().filter(reached::add).toList();
    }
    return reached;
  }
}
