package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.LoginSessionEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的技术登录会话仓储. */
public interface LoginSessionJpaRepository extends JpaRepository<LoginSessionEntity, String> {

  /**
   * 更新指定登录会话的撤销时间.
   *
   * @param id 目标记录的唯一编号
   * @param when 记录撤销时使用的时间
   */
  @Modifying
  @Query(
      """
      update LoginSessionEntity s set s.revokedAt = :when
      where s.id = :id and s.revokedAt is null
      """)
  void revoke(String id, Instant when);
}
