package cn.edu.upms.organization.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 员工存储，同时支持固定条件与动态分页筛选.
 *
 * <p>JpaSpecificationExecutor 接收 Service 构造的组合条件，部门隔离和关键字最终共同进入 SQL。这里的 exists、find 或 count
 * 都不会自动判断管理员资格，调用方须保持服务层范围校验.
 */
interface EmployeeRepository
    extends JpaRepository<EmployeeEntity, Long>, JpaSpecificationExecutor<EmployeeEntity> {
  /** 两个字段取 AND 共同匹配；查得员工后仍须检查 active. */
  Optional<EmployeeEntity> findByUsernameAndPhone(String username, String phone);

  Optional<EmployeeEntity> findByUsername(String username);

  long countBySuperAdminTrueAndActiveTrue();

  long countByDepartmentIdAndActiveTrue(Long departmentId);

  long countByCompanyIdAndActiveTrue(Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EmployeeEntity e where e.id = :id")
  Optional<EmployeeEntity> lock(@Param("id") long id);
}
