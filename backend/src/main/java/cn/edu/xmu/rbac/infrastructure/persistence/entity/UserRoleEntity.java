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
  /**
   * 对应关联表的组合主键.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  @Embeddable
  public record Key(Long userId, Long roleId) {}

  @EmbeddedId Key id;

  /** 供 Hibernate 加载持久化实体. */
  protected UserRoleEntity() {}

  /**
   * 构造待插入的用户角色记录.
   *
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  public UserRoleEntity(Long userId, Long roleId) {
    id = new Key(userId, roleId);
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
