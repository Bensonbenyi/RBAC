package cn.edu.upms.catalog.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 外部系统一个功能动作的目录记录，稳定 key 与显示名称分开维护.
 *
 * <p>(system_id, permission_key) 在数据库唯一，不同系统可以使用相同 key。停用保留目录
 * 记录以解释历史，当前角色选择中会移除被明确停用的项；重新启用不会自动恢复旧角色选择.
 */
@Entity
@Table(name = "permission_item")
class PermissionEntity extends PersistentRecord {
  Long systemId;
  String permissionKey;
  String moduleName;
  String featureName;
  String actionName;
  boolean active = true;

  protected PermissionEntity() {}
}
