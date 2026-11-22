package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的角色权限仓储. */
public interface RolePermissionJpaRepository
    extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Key> {

  @Modifying
  @Query("delete from RolePermissionEntity link where link.id = :key")
  void remove(RolePermissionEntity.Key key);
}
