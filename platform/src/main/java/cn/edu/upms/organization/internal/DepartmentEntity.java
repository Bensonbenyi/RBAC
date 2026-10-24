package cn.edu.upms.organization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 末级部门表映射，一行属于一个公司，不支持部门树.
 *
 * <p>companyId 在数据库有外键，但 Java 只持有编号，读取公司事实需通过模块自己的查询。 (company_id, name)
 * 唯一允许不同公司存在同名部门，不能仅按部门名称确定归属.
 */
@Entity
@Table(name = "department")
class DepartmentEntity extends PersistentRecord {
  Long companyId;
  String name;
  boolean active = true;

  protected DepartmentEntity() {}
}
