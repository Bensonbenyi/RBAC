package cn.edu.upms.integration.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 部门与系统多对多关系的一条关联，取消绑定时保留记录并设为无效.
 *
 * <p>数据库唯一键保证每对 systemId、departmentId 只有一行。active=true 只表示部门可用，
 * 不表示部门内员工自动拥有该系统角色；运行鉴权还会检查员工分配和当前角色.
 */
@Entity
@Table(name = "system_department")
class SystemDepartment extends PersistentRecord {
  Long systemId;
  Long departmentId;
  boolean active = true;

  protected SystemDepartment() {}
}
