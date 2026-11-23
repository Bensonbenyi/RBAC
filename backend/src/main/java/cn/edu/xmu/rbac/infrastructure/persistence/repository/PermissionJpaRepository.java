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

  /**
   * 按所属系统筛选权限.
   *
   * @param systemId 权限所属的业务系统编号
   * @param pageable JPA 分页及排序条件
   * @return 包含当前页记录及总数的分页结果
   */
  Page<PermissionEntity> findBySystemId(Long systemId, Pageable pageable);

  /**
   * 查询给定角色的直接权限并去重.
   *
   * @param roleIds 参与查询的角色编号集合
   * @return 满足查询条件的权限列表
   */
  @Query(
      """
      select distinct p from PermissionEntity p
      join RolePermissionEntity link on link.id.permissionId = p.id
      where link.id.roleId in :roleIds order by p.id
      """)
  List<PermissionEntity> forRoles(Collection<Long> roleIds);

  /**
   * 读取指定角色集合在目标系统内的权限编码.
   *
   * @param roleIds 参与查询的角色编号集合
   * @param systemKey 当前业务系统的唯一标识
   * @return 满足范围条件的编号或权限编码列表
   */
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
