package cn.edu.upms.integration.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 系统登记存储及系统行锁入口，框架按方法名生成普通查询.
 *
 * <p>管理授权遵循治理锁、员工、系统、角色的顺序；目录上报先锁自身系统再改目录。统一顺序减少交叉持锁等待，但锁不会独自验证角色归属或请求版本.
 */
interface SystemRepository extends JpaRepository<SystemEntity, Long> {
  Optional<SystemEntity> findBySystemKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SystemEntity s where s.id = :id")
  Optional<SystemEntity> lock(long id);
}
