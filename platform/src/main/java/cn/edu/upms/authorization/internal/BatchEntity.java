package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** 批量任务总体状态，不以整批数据库事务回滚已成功项. */
@Entity
@Table(name = "batch_job")
class BatchEntity extends PersistentRecord {
  String requestKey;
  Long actorId;
  Long systemId;
  Long roleId;
  boolean revokeRoles;
  String status;
  Instant createdAt;

  protected BatchEntity() {}
}
