package cn.edu.upms.organization.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 公司表映射；公司名称在数据库中全局唯一.
 *
 * <p>只有组织模块修改该对象。其他模块使用 companyId 与公开能力读取事实，避免跨模块追踪 JPA 对象关系。编号 1 的总公司行还被用作治理锁点，锁定不会修改公司内容.
 */
@Entity
@Table(name = "company")
class CompanyEntity extends PersistentRecord {
  String name;
  boolean active = true;

  protected CompanyEntity() {}
}
