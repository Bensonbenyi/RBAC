package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 每名员工每个系统一条版本化分配，数据库唯一约束提供最后防线. */
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
