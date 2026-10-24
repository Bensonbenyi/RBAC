package cn.edu.upms.authorization.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 批量员工项存储，通过 jobId 查询单个任务的全部执行状态.
 *
 * <p>数据库唯一键保证员工在同一任务中最多一项；逐项事务和可重试状态转换由 BatchService 维护。列表中包含成功项，重试必须显式排除它们.
 */
interface BatchItemRepository extends JpaRepository<BatchItemEntity, Long> {
  List<BatchItemEntity> findByJobId(long jobId);
}
