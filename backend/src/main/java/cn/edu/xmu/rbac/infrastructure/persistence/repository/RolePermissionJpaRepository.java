package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的角色权限仓储. */
public interface RolePermissionJpaRepository
    extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Key> {

  /**
   * 按复合主键删除授权关联.
   *
   * @param key 标识关联记录的复合主键
   */
  @Modifying
  @Query("delete from RolePermissionEntity link where link.id = :key")
  void remove(RolePermissionEntity.Key key);
}
