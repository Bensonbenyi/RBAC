package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_system")
class SystemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String systemKey;
  String name;

  protected SystemEntity() {}

  SystemEntity(String systemKey, String name) {
    this.systemKey = systemKey;
    this.name = name;
  }

  Data.System toData() {
    return new Data.System(id, systemKey, name);
  }
}
