package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.LoginSessionEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的技术登录会话仓储. */
public interface LoginSessionJpaRepository extends JpaRepository<LoginSessionEntity, String> {

  @Modifying
  @Query(
      """
      update LoginSessionEntity s set s.revokedAt = :when
      where s.id = :id and s.revokedAt is null
      """)
  void revoke(String id, Instant when);
}
