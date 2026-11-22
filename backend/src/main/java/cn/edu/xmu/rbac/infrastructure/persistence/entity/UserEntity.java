package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 用户账号的持久化映射. */
@Entity
@Table(name = "app_user")
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String username;
  String name;
  String phone;
  Long departmentId;

  protected UserEntity() {}

  public Data.User toData() {
    return new Data.User(id, username, name, phone, departmentId);
  }
}
