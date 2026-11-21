package cn.edu.xmu.rbac.infrastructure.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_role")
class UserRoleEntity implements Persistable<UserRoleEntity.Key> {
  @Embeddable
  record Key(Long userId, Long roleId) {}

  @EmbeddedId Key id;

  protected UserRoleEntity() {}

  UserRoleEntity(Long userId, Long roleId) {
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
