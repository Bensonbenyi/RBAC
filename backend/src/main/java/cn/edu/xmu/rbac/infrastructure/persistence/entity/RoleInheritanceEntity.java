package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

/** 角色继承的持久化映射. */
@Entity
@Table(name = "role_inheritance")
public class RoleInheritanceEntity implements Persistable<RoleInheritanceEntity.Key> {
  /** 对应关联表的组合主键. */
  @Embeddable
  public record Key(Long seniorRoleId, Long juniorRoleId) {}

  @EmbeddedId Key id;

  protected RoleInheritanceEntity() {}

  /** 构造待插入的角色继承记录. */
  public RoleInheritanceEntity(Long seniorRoleId, Long juniorRoleId) {
    id = new Key(seniorRoleId, juniorRoleId);
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
