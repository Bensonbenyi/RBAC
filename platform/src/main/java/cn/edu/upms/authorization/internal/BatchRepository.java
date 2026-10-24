package cn.edu.upms.authorization.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 批量任务存储，以操作者加 requestKey 定位一次幂等提交.
 *
 * <p>不同管理员可使用相同 key；本 Repository 不自动验证任务所有权，BatchService 还会校验操作者及任务中所有员工的当前管理范围.
 */
interface BatchRepository extends JpaRepository<BatchEntity, Long> {
  Optional<BatchEntity> findByActorIdAndRequestKey(long actorId, String key);
}
