package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.PermissionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的操作权限仓储. */
public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {

  Page<PermissionEntity> findBySystemId(Long systemId, Pageable pageable);

  @Query(
      """
      select distinct p from PermissionEntity p
      join RolePermissionEntity link on link.id.permissionId = p.id
      where link.id.roleId in :roleIds order by p.id
      """)
  List<PermissionEntity> forRoles(Collection<Long> roleIds);

  @Query(
      """
      select distinct p.code from PermissionEntity p
      join RolePermissionEntity link on link.id.permissionId = p.id
      join SystemEntity s on s.id = p.systemId
      where link.id.roleId in :roleIds and s.systemKey = :systemKey
      order by p.code
      """)
  List<String> codesForRoles(Collection<Long> roleIds, String systemKey);
}
