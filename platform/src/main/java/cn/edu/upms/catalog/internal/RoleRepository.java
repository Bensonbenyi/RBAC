package cn.edu.upms.catalog.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 角色存储；锁定角色后重新检查版本及状态. */
interface RoleRepository extends JpaRepository<RoleEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RoleEntity r where r.id = :id")
  Optional<RoleEntity> lock(long id);
}
