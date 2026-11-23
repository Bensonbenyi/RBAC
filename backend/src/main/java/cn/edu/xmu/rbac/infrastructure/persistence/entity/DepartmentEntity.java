package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 部门的持久化映射. */
@Entity
@Table(name = "department")
public class DepartmentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  String name;
  Long parentId;

  /** 供 Hibernate 加载持久化实体. */
  protected DepartmentEntity() {}

  /**
   * 将实体转换为业务层使用的数据快照.
   *
   * @return 目标部门的数据
   */
  public Data.Department toData() {
    return new Data.Department(id, name, parentId);
  }
}
