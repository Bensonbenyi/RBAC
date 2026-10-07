package cn.edu.upms.catalog.api;

import java.util.List;
import java.util.Set;

/** 权限目录和角色能力；目录描述外部功能，角色组合当前有效权限. */
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

  /** 完整目录中的一个功能动作；active=false 表示明确停用. */
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

  /** 复制模板为部门角色；后续模板更新不会传播. */
  Role copy(long actorId, long templateId, long departmentId, String name);

  /** 全量目录上报；相同版本相同内容幂等，错误报文不改变旧目录. */
  boolean report(long systemId, long revision, List<DirectoryItem> items);
}
