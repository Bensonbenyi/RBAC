package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleInheritanceEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的角色继承仓储. */
public interface RoleInheritanceJpaRepository
    extends JpaRepository<RoleInheritanceEntity, RoleInheritanceEntity.Key> {

  /**
   * 批量读取高级角色直接继承的基础角色编号.
   *
   * @param ids 参与查询的角色编号集合
   * @return 满足范围条件的编号或权限编码列表
   */
  @Query("select h.id.juniorRoleId from RoleInheritanceEntity h where h.id.seniorRoleId in :ids")
  List<Long> juniorIds(Collection<Long> ids);

  /**
   * 按复合主键删除授权关联.
   *
   * @param key 标识关联记录的复合主键
   */
  @Modifying
  @Query("delete from RoleInheritanceEntity link where link.id = :key")
  void remove(RoleInheritanceEntity.Key key);
}
