package cn.edu.upms.organization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 员工身份、归属与平台管理员资格的持久化映射.
 *
 * <p>username 与 phone 分别唯一，登录时要求两者共同匹配。departmentId 可为空，保留 54
 * 名源数据直属员工的待确认部门；公司与部门是否相互匹配仍由应用校验，两个外键无法证明它.
 *
 * <p>superAdmin 和 departmentAdmin 是平台管理身份；外部业务角色存于 role_assignment。 positionName 是资料，不据它自动任命管理员或授权.
 */
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
