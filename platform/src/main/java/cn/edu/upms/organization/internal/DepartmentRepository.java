package cn.edu.upms.organization.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 部门存储，只在组织模块内部使用.
 *
 * <p>findByCompanyId 按方法名生成条件查询，CompanyId 对应实体属性。Repository 解决 "怎么查"，管理员是否可查和停用前置条件由 Service
 * 决定，不能跳过用例直接给 Controller 使用.
 */
interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {
  List<DepartmentEntity> findByCompanyId(Long companyId);
}
