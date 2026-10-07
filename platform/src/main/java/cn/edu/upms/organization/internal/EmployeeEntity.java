package cn.edu.upms.organization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 员工身份及管理归属状态，只有本模块可以修改. */
@Entity
@Table(name = "employee")
class EmployeeEntity extends PersistentRecord {
  String username;
  String name;
  String phone;
  Long companyId;
  Long departmentId;
  String positionName;
  boolean active = true;
  boolean superAdmin;
  boolean departmentAdmin;

  protected EmployeeEntity() {}
}
