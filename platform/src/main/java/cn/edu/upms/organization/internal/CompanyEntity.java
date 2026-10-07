package cn.edu.upms.organization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 组织基础状态，只有本模块可以修改. */
@Entity
@Table(name = "company")
class CompanyEntity extends PersistentRecord {
  String name;
  boolean active = true;

  protected CompanyEntity() {}
}
