package cn.edu.upms.authorization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 批量操作的共同意图与总体状态，员工逐项结果保存在 batch_item.
 *
 * <p>(actor_id, request_key) 唯一识别一次提交；同一 key 不能被同一操作者用于不同内容。 RUNNING 表示正在处理，最终为 COMPLETED 或
 * PARTIAL_FAILURE；重启遗留任务标记为 INTERRUPTED，不根据总体状态猜测所有员工是否已授权.
 */
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
