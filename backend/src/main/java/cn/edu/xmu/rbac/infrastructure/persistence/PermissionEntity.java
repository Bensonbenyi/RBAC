package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permission")
class PermissionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long systemId;
  String code;
  String name;

  protected PermissionEntity() {}

  PermissionEntity(Long systemId, String code, String name) {
    this.systemId = systemId;
    this.code = code;
    this.name = name;
  }

  Data.Permission toData() {
    return new Data.Permission(id, systemId, code, name);
  }
}
