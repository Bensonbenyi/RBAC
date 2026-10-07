package cn.edu.upms.authorization.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 批量任务存储，幂等键在同一管理员范围内唯一. */
interface BatchRepository extends JpaRepository<BatchEntity, Long> {
  Optional<BatchEntity> findByActorIdAndRequestKey(long actorId, String key);
}
