package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 业务系统的持久化映射. */
@Entity
@Table(name = "business_system")
public class SystemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String systemKey;
  String name;

  protected SystemEntity() {}

  /** 构造待插入的业务系统记录. */
  public SystemEntity(String systemKey, String name) {
    this.systemKey = systemKey;
    this.name = name;
  }

  public Data.System toData() {
    return new Data.System(id, systemKey, name);
  }
}
