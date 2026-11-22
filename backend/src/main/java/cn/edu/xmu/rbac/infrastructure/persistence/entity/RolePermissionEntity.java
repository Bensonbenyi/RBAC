package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/** 角色权限的持久化映射. */
@Entity
@Table(name = "role_permission")
public class RolePermissionEntity implements Persistable<RolePermissionEntity.Key> {
  /** 对应关联表的组合主键. */
  @Embeddable
  public record Key(Long roleId, Long permissionId) {}

  @EmbeddedId Key id;

  protected RolePermissionEntity() {}

  /** 构造待插入的角色权限记录. */
  public RolePermissionEntity(Long roleId, Long permissionId) {
    id = new Key(roleId, permissionId);
  }

  @Override
  public Key getId() {
    return id;
  }

  /** 每个新建关联都执行插入，由组合主键拒绝重复. */
  @Override
  public boolean isNew() {
    return true;
  }
}
