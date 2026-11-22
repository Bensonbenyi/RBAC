package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/** 用户角色的持久化映射. */
@Entity
@Table(name = "user_role")
public class UserRoleEntity implements Persistable<UserRoleEntity.Key> {
  /** 对应关联表的组合主键. */
  @Embeddable
  public record Key(Long userId, Long roleId) {}

  @EmbeddedId Key id;

  protected UserRoleEntity() {}

  /** 构造待插入的用户角色记录. */
  public UserRoleEntity(Long userId, Long roleId) {
    id = new Key(userId, roleId);
  }

  @Override
  public Key getId() {
    return id;
  }

  /** 仓储只用这个实体插入关系；已存在的组合主键必须由数据库拒绝. */
  @Override
  public boolean isNew() {
    return true;
  }
}
