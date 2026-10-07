package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 单项状态与提交时确认的版本，不静默更新为数据库当前版本. */
@Entity
@Table(name = "batch_item")
class BatchItemEntity extends PersistentRecord {
  Long jobId;
  Long employeeId;
  long expectedVersion;
  String status;
  String message = "";

  protected BatchItemEntity() {}
}
