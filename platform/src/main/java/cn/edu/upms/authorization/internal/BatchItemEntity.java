package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 批量任务中的一名员工及其独立执行结果，(job_id, employee_id) 唯一.
 *
 * <p>expectedVersion 来自用户确认的分配版本，继承的 version 则是本任务项自己的 JPA 版本。二者不能混用。SUCCESS 与本项授权同事务提交；FAILED 或
 * INTERRUPTED 项重试前须刷新并确认分配版本，不能悄悄采用当前数据库值.
 */
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
