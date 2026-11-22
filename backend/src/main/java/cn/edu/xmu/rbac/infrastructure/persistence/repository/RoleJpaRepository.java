package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 的角色仓储. */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

  Page<RoleEntity> findByDepartmentId(Long departmentId, Pageable pageable);

  List<RoleEntity> findByIdInOrderById(Collection<Long> ids);
}
