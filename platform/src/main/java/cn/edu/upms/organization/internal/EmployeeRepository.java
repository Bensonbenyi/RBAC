package cn.edu.upms.organization.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 唯一资料查询和员工写竞争由数据库承担. */
interface EmployeeRepository
    extends JpaRepository<EmployeeEntity, Long>, JpaSpecificationExecutor<EmployeeEntity> {
  Optional<EmployeeEntity> findByUsernameAndPhone(String username, String phone);

  Optional<EmployeeEntity> findByUsername(String username);

  long countBySuperAdminTrueAndActiveTrue();

  long countByDepartmentIdAndActiveTrue(Long departmentId);

  long countByCompanyIdAndActiveTrue(Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EmployeeEntity e where e.id = :id")
  Optional<EmployeeEntity> lock(@Param("id") long id);
}
