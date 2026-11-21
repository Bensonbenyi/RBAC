package cn.edu.xmu.rbac.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RolePermissionJpaRepository
    extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Key> {

  @Modifying
  @Query("delete from RolePermissionEntity link where link.id = :key")
  void remove(RolePermissionEntity.Key key);
}
