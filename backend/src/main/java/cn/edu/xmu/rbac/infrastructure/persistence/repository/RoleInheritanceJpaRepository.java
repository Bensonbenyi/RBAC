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

  @Query("select h.id.juniorRoleId from RoleInheritanceEntity h where h.id.seniorRoleId in :ids")
  List<Long> juniorIds(Collection<Long> ids);

  @Modifying
  @Query("delete from RoleInheritanceEntity link where link.id = :key")
  void remove(RoleInheritanceEntity.Key key);
}
