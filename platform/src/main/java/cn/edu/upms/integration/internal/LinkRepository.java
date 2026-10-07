package cn.edu.upms.integration.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 系统部门关联存储，仅接入模块使用. */
interface LinkRepository extends JpaRepository<SystemDepartment, Long> {
  List<SystemDepartment> findBySystemId(long systemId);

  boolean existsBySystemIdAndDepartmentIdAndActiveTrue(long systemId, long departmentId);
}
