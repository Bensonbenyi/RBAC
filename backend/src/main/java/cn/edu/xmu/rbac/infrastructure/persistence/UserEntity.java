package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String username;
  String name;
  String phone;
  Long departmentId;

  protected UserEntity() {}

  UserEntity(String username, String name, String phone, Long departmentId) {
    this.username = username;
    this.name = name;
    this.phone = phone;
    this.departmentId = departmentId;
  }

  Data.User toData() {
    return new Data.User(id, username, name, phone, departmentId);
  }
}
