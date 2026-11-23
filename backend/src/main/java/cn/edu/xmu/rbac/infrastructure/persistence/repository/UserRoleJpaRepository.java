package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的用户角色仓储. */
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {

  /**
   * 读取用户直接持有的角色编号.
   *
   * @param userId 用户的唯一编号
   * @return 满足范围条件的编号或权限编码列表
   */
  @Query("select r.id.roleId from UserRoleEntity r where r.id.userId = :userId")
  List<Long> roleIds(Long userId);

  /**
   * 按复合主键删除授权关联.
   *
   * @param key 标识关联记录的复合主键
   */
  @Modifying
  @Query("delete from UserRoleEntity link where link.id = :key")
  void remove(UserRoleEntity.Key key);
}
