package cn.edu.upms.organization.api;

import cn.edu.upms.shared.PageResult;
import java.util.List;

/**
 * 组织资料、员工身份和管理范围的公开契约.
 *
 * <p>actorId 是执行操作的管理员，employeeId 是被操作员工；不能因为两个参数都为 long 就混用。
 * 除明确说明的可信事实读取外，管理能力会在服务端校验身份及部门范围。平台管理员身份与外部系统业务角色分别维护，任命管理员不会自动授予 OA 等系统的权限.
 *
 * <p>停用和调动涉及其他模块，入口在上层 EmployeeLifecycle；直接保存资料不能代替协调流程.
 */
public interface OrganizationApi {
  /**
   * 供模块协作使用的员工事实副本.
   *
   * <p>departmentId 为 {@code null} 表示源数据中的待确认归属，不能据此授予部门角色。 superAdmin 和 departmentAdmin
   * 表示平台管理身份；positionName 仅为岗位资料。 version 是当前持久化版本，更新时用于识别过时页面。手机号用于 Demo 登录，不写入审计.
   */
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

  /**
   * 用用户名和手机号同时匹配有效员工.
   *
   * <p>匹配失败和员工停用对外使用相同拒绝信息。手机号在这里是课程简化的校验资料，不具备密码、短信验证码或多因素认证的安全强度；本方法不创建统一会话.
   */
  Employee authenticate(String username, String phone);

  /** 校验管理员身份；最高管理员具有全局范围. */
  Employee administrator(long actorId);

  /**
   * 在调用者事务内取得管理写入共用的治理锁，并重新核验操作者.
   *
   * <p>先取得固定公司行锁，再校验最新管理员事实，使"取消管理员"和"管理员正在授权"按顺序发生。这是本课程为简化正确性采用的粗粒度方案，会限制管理写吞吐；普通鉴权读不取这把锁。
   * 调用方必须已开启事务，锁在该事务结束时释放，不能由前端调用独立的"加锁接口".
   */
  void lockManagement(long actorId);

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

  /**
   * 在已存在的事务内锁定员工，并返回从数据库重新读取的事实.
   *
   * <p>多个业务用例共同使用员工行作为竞争边界；返回后事务结束前其他冲突写入须等待。不能将返回的 record 当成可写实体，也不能在没有上层事务的情况下调用本方法.
   */
  Employee lockEmployee(long employeeId);

  /** 在已锁定的员工上改变状态，保留最高管理员不变量. */
  void setActive(long actorId, long employeeId, boolean active);

  /** 调动员工并清除旧部门管理员身份；协调者先核验已撤权. */
  void transfer(long actorId, long employeeId, long departmentId, long expectedVersion);

  /** 检查部门存在且有效，返回公司归属. */
  Department activeDepartment(long departmentId);
}
