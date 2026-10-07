package cn.edu.upms.organization.api;

import cn.edu.upms.shared.PageResult;
import java.util.List;

/** 组织与管理范围能力，实体留在模块内部. */
public interface OrganizationApi {
  /** 员工身份事实；手机号只用于资料维护与 Demo 登录，不写入审计. */
  record Employee(
      Long id,
      String username,
      String name,
      String phone,
      Long companyId,
      Long departmentId,
      String positionName,
      boolean active,
      boolean superAdmin,
      boolean departmentAdmin,
      long version) {}

  /** 公司事实. */
  record Company(Long id, String name, boolean active, long version) {}

  /** 末级部门事实. */
  record Department(Long id, Long companyId, String name, boolean active, long version) {}

  /** 员工资料变更；停用和部门调动必须使用协调用例. */
  record EmployeeChange(
      Long id,
      String username,
      String name,
      String phone,
      Long companyId,
      Long departmentId,
      String positionName,
      long version) {}

  /** 按员工标识获取归属与状态，供可信模块调用. */
  Employee employee(long id);

  /** 使用两个同时匹配的资料确认 Demo 身份. */
  Employee authenticate(String username, String phone);

  /** 校验管理员身份；最高管理员具有全局范围. */
  Employee administrator(long actorId);

  /** 校验操作者是否可维护指定员工. */
  void requireEmployeeScope(long actorId, long employeeId);

  /** 校验操作者是否可维护指定部门；null 仅用于全局模板. */
  void requireDepartmentScope(long actorId, Long departmentId);

  /** 查询范围内员工，服务端完成分页和隔离. */
  PageResult<Employee> employees(long actorId, String query, Long departmentId, int page, int size);

  /** 查询公司基础资料. */
  List<Company> companies(long actorId);

  /** 查询操作者范围内的部门. */
  List<Department> departments(long actorId);

  /** 新增或更新公司，停用前要求无有效关联. */
  Company saveCompany(long actorId, Long id, String name, boolean active, long version);

  /** 新增或更新部门；不支持下级部门. */
  Department saveDepartment(
      long actorId, Long id, Long companyId, String name, boolean active, long version);

  /** 保存员工资料，拒绝直接调动. */
  Employee saveEmployee(long actorId, EmployeeChange change);

  /** 任命或取消固定管理员身份，保留至少一名最高管理员. */
  Employee appoint(
      long actorId, long employeeId, boolean superAdmin, boolean departmentAdmin, long version);

  /** 参加调用者事务并锁定员工；换角、停用及调动共同使用此竞争边界. */
  Employee lockEmployee(long employeeId);

  /** 在已锁定的员工上改变状态，保留最高管理员不变量. */
  void setActive(long actorId, long employeeId, boolean active);

  /** 调动员工并清除旧部门管理员身份；协调者先核验已撤权. */
  void transfer(long actorId, long employeeId, long departmentId, long expectedVersion);

  /** 检查部门存在且有效，返回公司归属. */
  Department activeDepartment(long departmentId);
}
