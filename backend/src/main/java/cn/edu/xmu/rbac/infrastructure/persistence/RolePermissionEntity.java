package cn.edu.xmu.rbac.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permission")
class RolePermissionEntity {
  @Embeddable
  record Key(Long roleId, Long permissionId) {}

  @EmbeddedId Key id;

  protected RolePermissionEntity() {}

  RolePermissionEntity(Long roleId, Long permissionId) {
    id = new Key(roleId, permissionId);
  }
}
