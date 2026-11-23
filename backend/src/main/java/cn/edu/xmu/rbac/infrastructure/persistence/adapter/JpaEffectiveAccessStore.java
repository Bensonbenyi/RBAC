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

  /**
   * 组装直接角色、继承边和权限目录的查询仓储.
   *
   * @param userRoles 用户角色关联的 JPA 仓储
   * @param inheritance 角色继承关联的 JPA 仓储
   * @param roles 角色 JPA 仓储
   * @param permissions 权限 JPA 仓储
   */
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

  /**
   * 查询角色直接权限和继承权限的并集.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的权限列表
   */
  @Override
  public List<Data.Permission> effectiveRolePermissions(Long roleId) {
    return permissions.forRoles(reachableRoles(List.of(roleId))).stream()
        .map(PermissionEntity::toData)
        .toList();
  }

  /**
   * 查询用户在指定系统内的有效权限码.
   *
   * @param userId 用户的唯一编号
   * @param systemKey 当前业务系统的唯一标识
   * @return 满足范围条件的编号或权限编码列表
   */
  @Override
  public List<String> permissionsForUser(Long userId, String systemKey) {
    Set<Long> effectiveRoles = reachableRoles(userRoles.roleIds(userId));
    return effectiveRoles.isEmpty()
        ? List.of()
        : permissions.codesForRoles(effectiveRoles, systemKey);
  }

  /**
   * 读取用户可达角色的名称.
   *
   * @param userId 用户的唯一编号
   * @return 满足查询条件的角色列表
   */
  @Override
  public List<String> rolesForUser(Long userId) {
    return roles.findByIdInOrderById(reachableRoles(userRoles.roleIds(userId))).stream()
        .map(role -> role.toData().name())
        .toList();
  }

  /**
   * 逐层遍历继承边并去重.
   *
   * @param roots 开始查询的直接角色编号
   * @return 包含起点的去重可达角色编号集合
   */
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
