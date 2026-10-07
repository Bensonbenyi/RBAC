package cn.edu.upms.organization.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 部门信息不通过组织模块边界泄漏. */
interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
  List<DepartmentEntity> findByCompanyId(Long companyId);
}
