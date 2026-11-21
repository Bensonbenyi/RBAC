package cn.edu.xmu.rbac.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

  Page<RoleEntity> findByDepartmentId(Long departmentId, Pageable pageable);

  List<RoleEntity> findByIdInOrderById(Collection<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RoleEntity r where r.id = :id")
  Optional<RoleEntity> lockById(Long id);
}
