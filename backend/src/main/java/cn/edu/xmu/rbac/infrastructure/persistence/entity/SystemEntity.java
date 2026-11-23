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

  /** 供 Hibernate 加载持久化实体. */
  protected SystemEntity() {}

  /**
   * 将实体转换为业务层使用的数据快照.
   *
   * @return 业务系统资料
   */
  public Data.System toData() {
    return new Data.System(id, systemKey, name);
  }
}
