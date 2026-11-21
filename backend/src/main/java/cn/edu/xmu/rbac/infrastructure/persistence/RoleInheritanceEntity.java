package cn.edu.xmu.rbac.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_inheritance")
class RoleInheritanceEntity {
  @Embeddable
  record Key(Long seniorRoleId, Long juniorRoleId) {}

  @EmbeddedId Key id;

  protected RoleInheritanceEntity() {}

  RoleInheritanceEntity(Long seniorRoleId, Long juniorRoleId) {
    id = new Key(seniorRoleId, juniorRoleId);
  }
}
