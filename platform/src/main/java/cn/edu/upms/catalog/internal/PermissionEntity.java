package cn.edu.upms.catalog.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 功能点与动作的有效状态；角色保留引用以解释历史停用影响. */
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
