package cn.edu.upms.organization.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.organization.api.OrganizationApi.EmployeeChange;
import cn.edu.upms.shared.BusinessException;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 使用独立状态夹具覆盖组织写入不变量，不依赖教师数据中的偶然岗位与人数. */
class OrganizationServiceTest {
  final CompanyRepository companies = mock(CompanyRepository.class);
  final DepartmentRepository departments = mock(DepartmentRepository.class);
  final EmployeeRepository employees = mock(EmployeeRepository.class);
  final AuditApi audit = mock(AuditApi.class);
  final EntityManager manager = mock(EntityManager.class);
  final OrganizationService service =
      new OrganizationService(companies, departments, employees, audit, manager);
  final Map<Long, EmployeeEntity> people = new HashMap<>();
  final CompanyEntity company = new CompanyEntity();
  final DepartmentEntity department = new DepartmentEntity();

  @BeforeEach
  void fixtures() {
    ReflectionTestUtils.setField(company, "id", 1L);
    company.name = "总公司";
    ReflectionTestUtils.setField(department, "id", 1L);
    department.companyId = 1L;
    department.name = "部门";
    people.put(1L, person(1, true, false, 1L));
    people.put(2L, person(2, false, true, 1L));
    people.put(3L, person(3, false, false, 1L));
    when(companies.lock(anyLong())).thenReturn(Optional.of(company));
    when(companies.findById(anyLong())).thenReturn(Optional.of(company));
    when(companies.findAll()).thenReturn(List.of(company));
    when(departments.findById(anyLong())).thenReturn(Optional.of(department));
    when(departments.findAll()).thenReturn(List.of(department));
    when(employees.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(people.get(call.getArgument(0))));
    when(companies.saveAndFlush(any())).thenAnswer(call -> identify(call.getArgument(0)));
    when(departments.saveAndFlush(any())).thenAnswer(call -> identify(call.getArgument(0)));
    when(employees.saveAndFlush(any())).thenAnswer(call -> identify(call.getArgument(0)));
  }

  @Test
  @DisplayName("ACC-F-002/007 身份停用、部门停用及跨部门范围均拒绝")
  void identitiesAndScope() {
    var worker = people.get(3L);
    when(employees.findByUsernameAndPhone("worker", "phone")).thenReturn(Optional.of(worker));
    assertThat(service.authenticate("worker", "phone").id()).isEqualTo(3);
    worker.active = false;
    denied(() -> service.authenticate("worker", "phone"));
    denied(() -> service.authenticate("unknown", "phone"));
    denied(() -> service.administrator(3));
    worker.active = true;
    denied(() -> service.administrator(3));
    assertThat(service.administrator(1).superAdmin()).isTrue();
    service.requireEmployeeScope(2, 3);
    worker.departmentId = 2L;
    denied(() -> service.requireEmployeeScope(2, 3));
    service.requireEmployeeScope(1, 3);
    service.requireDepartmentScope(2, 1L);
    denied(() -> service.requireDepartmentScope(2, 2L));
    people.get(2L).departmentId = null;
    denied(() -> service.administrator(2));
    people.get(2L).departmentId = 1L;
    department.active = false;
    denied(() -> service.administrator(2));
    department.active = true;
    assertThat(service.departments(1)).hasSize(1);
    assertThat(service.departments(2)).hasSize(1);
    people.get(2L).departmentId = 2L;
    assertThat(service.departments(2)).isEmpty();
    assertThat(service.companies(1)).hasSize(1);
    denied(() -> service.employee(999));
  }

  @Test
  @DisplayName("公司和部门维护要求有效关联已处理，旧版本及归属变化均拒绝")
  void companyAndDepartmentRules() {
    assertThat(service.saveCompany(1, null, " 新公司 ", true, 0).name()).isEqualTo("新公司");
    denied(() -> service.saveCompany(1, 1L, "总公司", true, 1));
    when(employees.countByCompanyIdAndActiveTrue(1L)).thenReturn(1L);
    denied(() -> service.saveCompany(1, 1L, "总公司", false, 0));
    when(employees.countByCompanyIdAndActiveTrue(1L)).thenReturn(0L);
    when(departments.findByCompanyId(1L)).thenReturn(List.of(department));
    denied(() -> service.saveCompany(1, 1L, "总公司", false, 0));
    department.active = false;
    assertThat(service.saveCompany(1, 1L, "总公司", false, 0).active()).isFalse();
    denied(() -> service.saveDepartment(1, null, 1L, "部门", true, 0));
    company.active = true;
    department.active = true;
    assertThat(service.saveDepartment(1, null, 1L, "新部门", true, 0).name()).isEqualTo("新部门");
    assertThat(service.saveDepartment(1, 1L, 1L, "部门", true, 0).active()).isTrue();
    denied(() -> service.saveDepartment(1, 1L, 1L, "部门", true, 1));
    denied(() -> service.saveDepartment(1, 1L, 2L, "部门", true, 0));
    when(employees.countByDepartmentIdAndActiveTrue(1L)).thenReturn(1L);
    denied(() -> service.saveDepartment(1, 1L, 1L, "部门", false, 0));
    when(employees.countByDepartmentIdAndActiveTrue(1L)).thenReturn(0L);
    assertThat(service.saveDepartment(1, 1L, 1L, "部门", false, 0).active()).isFalse();
    department.active = true;
    denied(() -> service.saveCompany(2, null, "公司", true, 0));
    for (String name : new String[] {null, " "}) {
      denied(() -> service.saveCompany(1, null, name, true, 0));
    }
  }

  @Test
  @DisplayName("员工资料维护不能绕过调动流程，部门和公司须匹配")
  void employeeChanges() {
    assertThat(service.saveEmployee(1, change(null, 1L, 1L, 0)).name()).isEqualTo("员工");
    assertThat(service.saveEmployee(1, change(3L, 1L, 1L, 0)).id()).isEqualTo(3);
    denied(() -> service.saveEmployee(1, change(3L, 1L, 1L, 1)));
    denied(() -> service.saveEmployee(1, change(3L, 1L, 2L, 0)));
    company.active = false;
    denied(() -> service.saveEmployee(1, change(null, 1L, 1L, 0)));
    company.active = true;
    denied(() -> service.saveEmployee(1, change(null, 2L, 1L, 0)));
    assertThat(service.saveEmployee(1, change(null, 1L, null, 0)).departmentId()).isNull();
    // 普通的部门管理员没有空范围，前面的范围检查就必须阻止该请求。
    denied(() -> service.saveEmployee(2, change(null, 1L, null, 0)));
    people.get(3L).departmentId = null;
    assertThat(service.saveEmployee(1, change(3L, 1L, 1L, 0)).departmentId()).isEqualTo(1);
  }

  @Test
  @DisplayName("ACC-F-018 管理员任命与最后一名管理员保护，调动清除部门管理身份")
  void administratorsAndTransfer() {
    denied(() -> service.appoint(1, 3, false, true, 1));
    denied(() -> service.appoint(1, 1, false, false, 0));
    people.get(3L).active = false;
    denied(() -> service.appoint(1, 3, true, false, 0));
    people.get(3L).active = true;
    people.get(3L).departmentId = null;
    denied(() -> service.appoint(1, 3, false, true, 0));
    assertThat(service.appoint(1, 3, true, false, 0).superAdmin()).isTrue();
    when(employees.countBySuperAdminTrueAndActiveTrue()).thenReturn(1L);
    denied(() -> service.setActive(1, 1, false));
    denied(() -> service.appoint(1, 3, false, false, 0));
    when(employees.countBySuperAdminTrueAndActiveTrue()).thenReturn(2L);
    assertThat(service.appoint(1, 3, false, false, 0).superAdmin()).isFalse();
    people.get(3L).superAdmin = true;
    people.get(3L).active = false;
    service.setActive(1, 3, false);
    assertThat(service.employee(3).active()).isFalse();
    assertThat(service.appoint(1, 1, true, false, 0).superAdmin()).isTrue();
    people.get(3L).superAdmin = false;
    service.setActive(1, 3, false);
    assertThat(service.employee(3).active()).isFalse();
    service.setActive(1, 3, true);
    assertThat(service.lockEmployee(3).active()).isTrue();
    denied(() -> service.transfer(1, 3, 2, 1));
    service.transfer(1, 2, 2, 0);
    assertThat(service.employee(2).departmentAdmin()).isFalse();
  }

  private static EmployeeEntity person(long id, boolean global, boolean admin, Long department) {
    var employee = new EmployeeEntity();
    ReflectionTestUtils.setField(employee, "id", id);
    employee.superAdmin = global;
    employee.departmentAdmin = admin;
    employee.companyId = 1L;
    employee.departmentId = department;
    employee.name = "员工";
    return employee;
  }

  private static EmployeeChange change(Long id, Long company, Long department, long version) {
    return new EmployeeChange(id, "user", "员工", "13900000000", company, department, "岗位", version);
  }

  private static <T> T identify(T entity) {
    if (ReflectionTestUtils.getField(entity, "id") == null) {
      ReflectionTestUtils.setField(entity, "id", 100L);
    }
    return entity;
  }

  private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOf(BusinessException.class);
  }
}
