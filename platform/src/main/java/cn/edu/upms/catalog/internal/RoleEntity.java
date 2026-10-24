package cn.edu.upms.catalog.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * 全局模板与部门业务角色共用的表映射，权限选择落在 role_permission 关联表.
 *
 * <p>template=true 时 departmentId 必须为空；普通角色必须属于某部门，这个组合规则由应用维护。数据库同范围名称唯一键对 NULL
 * 部门不能独自限制模板重名，服务层还会在治理锁内检查.
 *
 * <p>deleted 表示逻辑删除并隐藏历史角色，active 仅表示当前启停。permissions 是权限编号值集合，未映射为另一个模块实体的集合；访问它需要在本模块事务内完成.
 */
@Entity
@Table(name = "business_role")
class RoleEntity extends PersistentRecord {
  Long systemId;
  Long departmentId;
  String name;
  boolean template;
  boolean active = true;
  boolean deleted;
  // 权限集合单独存在关联表；显式推进内容字段，确保集合变更也可被实体版本表达。
  long contentRevision;

  // JPA 把 Set<Long> 映射为 role_permission 的多行；集合是当前角色拥有的值，不向外泄漏。
  @ElementCollection
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_id")
  Set<Long> permissions = new HashSet<>();

  protected RoleEntity() {}
}
