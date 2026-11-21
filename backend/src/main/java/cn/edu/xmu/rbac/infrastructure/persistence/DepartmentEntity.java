package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
class DepartmentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String name;
  Long parentId;

  protected DepartmentEntity() {}

  DepartmentEntity(String name, Long parentId) {
    this.name = name;
    this.parentId = parentId;
  }

  Data.Department toData() {
    return new Data.Department(id, name, parentId);
  }
}
