package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "`role`")
class RoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String name;
  Long departmentId;

  protected RoleEntity() {}

  RoleEntity(String name, Long departmentId) {
    this.name = name;
    this.departmentId = departmentId;
  }

  Data.Role toData() {
    return new Data.Role(id, name, departmentId);
  }
}
