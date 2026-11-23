package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 的角色仓储. */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

  /**
   * 按部门筛选持久化记录.
   *
   * @param departmentId 所属部门编号
   * @param pageable JPA 分页及排序条件
   * @return 包含当前页记录及总数的分页结果
   */
  Page<RoleEntity> findByDepartmentId(Long departmentId, Pageable pageable);

  /**
   * 按编号集合取角色并按编号升序排列.
   *
   * @param ids 参与查询的角色编号集合
   * @return 满足查询条件的角色列表
   */
  List<RoleEntity> findByIdInOrderById(Collection<Long> ids);
}
