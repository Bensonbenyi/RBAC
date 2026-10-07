package cn.edu.upms.organization.api;

import java.util.List;
import java.util.Map;

/** 启动时的课程资料导入能力，不映射到 HTTP，也不按岗位推断授权. */
public interface OrganizationImportApi {
  /** 经过源资料校验的公司记录. */
  record CompanyRow(long id, String name) {}

  /** 经过源资料校验的末级部门记录. */
  record DepartmentRow(long id, long companyId, String name) {}

  /** 员工记录；null 部门保留源资料中的待确认状态. */
  record EmployeeRow(
      long id,
      String username,
      String name,
      String phone,
      long companyId,
      Long departmentId,
      String position) {}

  /** 在空组织数据集导入基础事实；外层事务负责整批提交. */
  void load(
      List<CompanyRow> companies, List<DepartmentRow> departments, List<EmployeeRow> employees);

  /** 读取实际导入数量，供验收与启动检查使用. */
  Map<String, Long> counts();

  /** 仅由独立演示配置调用，明确指定账号的管理身份. */
  void demonstrationAdministrators(List<String> superUsers, List<String> departmentUsers);
}
