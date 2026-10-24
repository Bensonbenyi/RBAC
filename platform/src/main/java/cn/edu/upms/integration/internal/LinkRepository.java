package cn.edu.upms.integration.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统部门关联存储，区分查询历史行与判断当前有效关联.
 *
 * <p>findBySystemId 包括无效行，供范围替换时复用；existsBy...AndActiveTrue 只判断当前关联。
 * 本查询不判断系统自身是否启用，IntegrationService.available 会综合两个条件.
 */
interface LinkRepository extends JpaRepository<SystemDepartment, Long> {
  List<SystemDepartment> findBySystemId(long systemId);

  boolean existsBySystemIdAndDepartmentIdAndActiveTrue(long systemId, long departmentId);
}
