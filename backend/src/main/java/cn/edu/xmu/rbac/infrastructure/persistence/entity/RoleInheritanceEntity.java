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
  /**
   * 对应关联表的组合主键.
   *
   * @param seniorRoleId 高级角色的唯一编号
   * @param juniorRoleId 基础角色的唯一编号
   */
  @Embeddable
  public record Key(Long seniorRoleId, Long juniorRoleId) {}

  @EmbeddedId Key id;

  /** 供 Hibernate 加载持久化实体. */
  protected RoleInheritanceEntity() {}

  /**
   * 构造待插入的角色继承记录.
   *
   * @param seniorRoleId 高级角色的唯一编号
   * @param juniorRoleId 基础角色的唯一编号
   */
  public RoleInheritanceEntity(Long seniorRoleId, Long juniorRoleId) {
    id = new Key(seniorRoleId, juniorRoleId);
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
