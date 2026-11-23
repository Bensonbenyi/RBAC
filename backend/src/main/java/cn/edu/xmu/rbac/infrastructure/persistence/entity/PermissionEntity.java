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

  /** 供 Hibernate 加载持久化实体. */
  protected PermissionEntity() {}

  /**
   * 将实体转换为业务层使用的数据快照.
   *
   * @return 目标权限的数据
   */
  public Data.Permission toData() {
    return new Data.Permission(id, systemId, code, name);
  }
}
