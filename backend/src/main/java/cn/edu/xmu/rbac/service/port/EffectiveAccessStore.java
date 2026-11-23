package cn.edu.xmu.rbac.service.port;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 合并直接授权和继承授权后的只读查询. */
public interface EffectiveAccessStore {
  /**
   * 批量逐层读取继承关系，返回直接角色及全部可达的基础角色.
   *
   * @param roots 开始查询的直接角色编号
   * @return 包含起点的去重可达角色编号集合
   */
  Set<Long> reachableRoles(Collection<Long> roots);

  /**
   * 查询角色直接权限和继承权限的并集.
   *
   * @param roleId 角色的唯一编号
   * @return 满足查询条件的权限列表
   */
  List<Data.Permission> effectiveRolePermissions(Long roleId);

  /**
   * 查询用户在指定系统内的有效权限码.
   *
   * @param userId 用户的唯一编号
   * @param systemKey 当前业务系统的唯一标识
   * @return 满足范围条件的编号或权限编码列表
   */
  List<String> permissionsForUser(Long userId, String systemKey);

  /**
   * 读取用户可达角色的名称.
   *
   * @param userId 用户的唯一编号
   * @return 满足查询条件的角色列表
   */
  List<String> rolesForUser(Long userId);
}
