package cn.edu.upms.catalog.api;

import java.util.List;
import java.util.Set;

/**
 * 权限目录、全局模板与部门角色的公开契约.
 *
 * <p>目录定义"该系统有哪些功能动作"，角色定义"选择了哪些动作"。角色存在不代表员工已获得角色，员工分配由授权模块维护。模板复制后形成独立部门角色，后续模板变化不会自动扩权.
 */
public interface CatalogApi {
  /** 权限事实；permissionKey 由业务系统稳定定义，不以显示名称替代. */
  record Permission(
      Long id,
      Long systemId,
      String key,
      String module,
      String feature,
      String action,
      boolean active,
      long version) {}

  /** 角色事实；模板没有部门，且不能直接分配给员工. */
  record Role(
      Long id,
      Long systemId,
      Long departmentId,
      String name,
      boolean template,
      boolean active,
      long version,
      Set<Long> permissions) {}

  /**
   * 外部系统上报的一个稳定功能动作.
   *
   * @param key 系统内稳定且唯一的权限标识，改显示名称不能改变它
   * @param module 所属业务模块的显示名称
   * @param feature 功能点的显示名称
   * @param action 操作动作，例如查看、编辑或本人查看
   * @param active 是否有效；停用现有有效项必须明确传 {@code false}
   */
  record DirectoryItem(String key, String module, String feature, String action, boolean active) {}

  /** 角色修改意图；归属建立后固定，权限集采用显式替换. */
  record RoleChange(
      Long id,
      Long systemId,
      Long departmentId,
      String name,
      boolean template,
      boolean active,
      long version,
      Set<Long> permissions) {}

  /** 读取可信模块所需角色事实. */
  Role role(long roleId);

  /** 在调用者事务内锁角色；先锁员工与系统再调用. */
  Role lock(long roleId);

  /** 返回系统完整目录，包括停用项以便解释影响. */
  List<Permission> permissions(long systemId);

  /** 查询管理范围内角色及该范围可用系统的全局模板. */
  List<Role> roles(long actorId, Long systemId, Long departmentId);

  /** 新建、更新或停用角色；新增权限不会自动加入已有角色. */
  Role save(long actorId, RoleChange change);

  /** 删除已由上层确认无员工分配的角色；历史资料保留，前台不再显示. */
  void delete(long actorId, long roleId, long expectedVersion);

  /** 复制模板为部门角色；后续模板更新不会传播. */
  Role copy(long actorId, long templateId, long departmentId, String name);

  /**
   * 接收已认证系统的完整权限目录，并在一个事务内更新目录与版本.
   *
   * <p>不能遗漏旧的有效项，停用须显式声明；旧的停用历史项可以省略。同版本仅接受等价内容，便于外部系统因网络问题重报。更高版本新增权限不会自动加入角色，停用会移除现有角色选择.
   *
   * @param systemId 从目录令牌推导出的系统编号，不能由上报方任意指定
   * @param revision 外部系统维护的正整数内容版本，与 JPA 版本不同
   * @param items 本次完整目录，最多 10,000 项
   * @return 新版本实际落库为 {@code true}；等价重报为 {@code false}，都属于接受成功
   */
  boolean report(long systemId, long revision, List<DirectoryItem> items);
}
