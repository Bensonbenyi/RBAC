package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RoleInheritanceJpaRepository
    extends JpaRepository<RoleInheritanceEntity, RoleInheritanceEntity.Key> {

  @Query(
      """
      select new cn.edu.xmu.rbac.service.port.Data$Inheritance(h.id.seniorRoleId, h.id.juniorRoleId)
      from RoleInheritanceEntity h join RoleEntity r on r.id = h.id.seniorRoleId
      where r.departmentId = :departmentId
      """)
  List<Data.Inheritance> edgesInDepartment(Long departmentId);

  @Query("select h.id.juniorRoleId from RoleInheritanceEntity h where h.id.seniorRoleId in :ids")
  List<Long> juniorIds(Collection<Long> ids);

  @Modifying
  @Query("delete from RoleInheritanceEntity link where link.id = :key")
  void remove(RoleInheritanceEntity.Key key);
}
