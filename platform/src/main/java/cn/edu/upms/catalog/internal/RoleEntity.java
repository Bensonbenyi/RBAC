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

/** 角色权限集合由本模块维护；全局模板与部门角色共享显式规则. */
@Entity
@Table(name = "business_role")
class RoleEntity extends PersistentRecord {
  Long systemId;
  Long departmentId;
  String name;
  boolean template;
  boolean active = true;
  boolean deleted;
  long contentRevision;

  @ElementCollection
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_id")
  Set<Long> permissions = new HashSet<>();

  protected RoleEntity() {}
}
