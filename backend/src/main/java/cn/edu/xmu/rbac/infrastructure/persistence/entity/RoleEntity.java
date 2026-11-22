package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 角色的持久化映射. */
@Entity
@Table(name = "`role`")
public class RoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String name;
  Long departmentId;

  protected RoleEntity() {}

  /** 构造待插入的角色记录. */
  public RoleEntity(String name, Long departmentId) {
    this.name = name;
    this.departmentId = departmentId;
  }

  /** 更新可编辑的显示名称. */
  public void rename(String name) {
    this.name = name;
  }

  public Data.Role toData() {
    return new Data.Role(id, name, departmentId);
  }
}
