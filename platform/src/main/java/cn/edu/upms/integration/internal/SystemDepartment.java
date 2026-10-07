package cn.edu.upms.integration.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 部门可用系统关联；历史关联保留并以状态撤销. */
@Entity
@Table(name = "system_department")
class SystemDepartment extends PersistentRecord {
  Long systemId;
  Long departmentId;
  boolean active = true;

  protected SystemDepartment() {}
}
