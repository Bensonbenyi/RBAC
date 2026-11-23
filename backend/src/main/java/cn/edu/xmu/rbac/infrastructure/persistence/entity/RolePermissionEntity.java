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
  /**
   * 对应关联表的组合主键.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  @Embeddable
  public record Key(Long roleId, Long permissionId) {}

  @EmbeddedId Key id;

  /** 供 Hibernate 加载持久化实体. */
  protected RolePermissionEntity() {}

  /**
   * 构造待插入的角色权限记录.
   *
   * @param roleId 角色的唯一编号
   * @param permissionId 权限的唯一编号
   */
  public RolePermissionEntity(Long roleId, Long permissionId) {
    id = new Key(roleId, permissionId);
  }

  /**
   * 读取关联实体的复合主键.
   *
   * @return 关联记录的复合主键
   */
  @Override
  public Key getId() {
    return id;
  }

  /**
   * 告知 Spring Data JPA 此关联需要执行插入.
   *
   * @return 始终为 true，使新增关联执行数据库插入
   */
  @Override
  public boolean isNew() {
    return true;
  }
}
