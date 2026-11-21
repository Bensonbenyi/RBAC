package cn.edu.xmu.rbac.infrastructure.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface LoginSessionJpaRepository extends JpaRepository<LoginSessionEntity, String> {

  @Modifying
  @Query("delete from LoginSessionEntity s where s.userId = :userId")
  void removeByUserId(Long userId);

  @Modifying
  @Query(
      """
      update LoginSessionEntity s set s.revokedAt = :when
      where s.id = :id and s.revokedAt is null
      """)
  void revoke(String id, Instant when);
}
