package cn.edu.upms.catalog.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 角色存储与写入行锁入口，仅在目录角色模块内部使用.
 *
 * <p>锁方法保证同一角色的冲突写入排队；调用方仍须检查 deleted、active、归属和旧版本。查到一条实体本身不代表它可显示或可分配.
 */
interface RoleRepository extends JpaRepository<RoleEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RoleEntity r where r.id = :id")
  Optional<RoleEntity> lock(long id);
}
