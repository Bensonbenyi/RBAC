package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 一名员工在一个系统中最多一条角色分配，唯一键为 (employee_id, system_id).
 *
 * <p>roleId 可为空：撤权把它清空并保留 revoked、changedAt 和版本，而不是删除记录后又从
 * 版本零开始。外键保证引用存在，员工、角色、部门与系统是否匹配仍须在写入事务内校验.
 */
@Entity
@Table(name = "role_assignment")
class AssignmentEntity extends PersistentRecord {
  Long employeeId;
  Long systemId;
  Long roleId;
  boolean revoked = true;
  Instant changedAt;

  protected AssignmentEntity() {}
}
