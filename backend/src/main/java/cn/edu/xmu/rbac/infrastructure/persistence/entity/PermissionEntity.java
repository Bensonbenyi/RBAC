package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 操作权限的持久化映射. */
@Entity
@Table(name = "permission")
public class PermissionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Long systemId;
  String code;
  String name;

  protected PermissionEntity() {}

  /** 构造待插入的操作权限记录. */
  public PermissionEntity(Long systemId, String code, String name) {
    this.systemId = systemId;
    this.code = code;
    this.name = name;
  }

  /** 更新可编辑的显示名称. */
  public void rename(String name) {
    this.name = name;
  }

  public Data.Permission toData() {
    return new Data.Permission(id, systemId, code, name);
  }
}
