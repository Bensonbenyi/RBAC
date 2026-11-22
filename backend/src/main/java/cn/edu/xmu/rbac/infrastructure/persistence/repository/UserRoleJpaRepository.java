package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的用户角色仓储. */
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {

  @Query("select r.id.roleId from UserRoleEntity r where r.id.userId = :userId")
  List<Long> roleIds(Long userId);

  @Modifying
  @Query("delete from UserRoleEntity link where link.id = :key")
  void remove(UserRoleEntity.Key key);
}
