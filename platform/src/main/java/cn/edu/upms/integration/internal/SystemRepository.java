package cn.edu.upms.integration.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 系统存储，锁顺序为员工、系统、角色、分配记录. */
interface SystemRepository extends JpaRepository<SystemEntity, Long> {
  Optional<SystemEntity> findBySystemKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SystemEntity s where s.id = :id")
  Optional<SystemEntity> lock(long id);
}
