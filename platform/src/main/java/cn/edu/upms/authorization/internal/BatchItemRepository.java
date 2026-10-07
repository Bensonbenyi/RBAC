package cn.edu.upms.authorization.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 单项任务存储，员工在一个任务中最多出现一次. */
interface BatchItemRepository extends JpaRepository<BatchItemEntity, Long> {
  List<BatchItemEntity> findByJobId(long jobId);
}
