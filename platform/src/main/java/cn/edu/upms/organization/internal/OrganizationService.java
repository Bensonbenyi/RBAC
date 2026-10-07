package cn.edu.upms.organization.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import cn.edu.upms.shared.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 维护组织事实与管理员范围；员工停用的副作用由上层协调. */
@Service
@Transactional(readOnly = true)
public class OrganizationService implements OrganizationApi {
  private final CompanyRepository companyRepository;
  private final DepartmentRepository departmentRepository;
  private final EmployeeRepository employeeRepository;
  private final AuditApi audit;
  private final EntityManager entityManager;

  /** 注入本模块存储与审计公开能力. */
  public OrganizationService(
      CompanyRepository companyRepository,
      DepartmentRepository departmentRepository,
      EmployeeRepository employeeRepository,
      AuditApi audit,
      EntityManager entityManager) {
    this.companyRepository = companyRepository;
    this.departmentRepository = departmentRepository;
    this.employeeRepository = employeeRepository;
    this.audit = audit;
    this.entityManager = entityManager;
  }

  @Override
  public OrganizationApi.Employee employee(long id) {
    return view(find(id));
  }

  @Override
  public OrganizationApi.Employee authenticate(String username, String phone) {
    EmployeeEntity employee =
        employeeRepository
            .findByUsernameAndPhone(username, phone)
            .orElseThrow(() -> new BusinessException(401, "LOGIN_REJECTED", "登录资料不匹配或身份已停用"));
    if (!employee.active) {
      throw new BusinessException(401, "LOGIN_REJECTED", "登录资料不匹配或身份已停用");
    }
    return view(employee);
  }

  @Override
  public OrganizationApi.Employee administrator(long actorId) {
    EmployeeEntity actor = find(actorId);
    if (!actor.active || !(actor.superAdmin || actor.departmentAdmin)) {
      throw BusinessException.forbidden();
    }
    if (!actor.superAdmin
        && (actor.departmentId == null || !activeDepartment(actor.departmentId).active())) {
      throw BusinessException.forbidden();
    }
    return view(actor);
  }

  @Override
  public void requireEmployeeScope(long actorId, long employeeId) {
    OrganizationApi.Employee actor = administrator(actorId);
    OrganizationApi.Employee target = employee(employeeId);
    if (!actor.superAdmin() && !Objects.equals(actor.departmentId(), target.departmentId())) {
      throw BusinessException.forbidden();
    }
  }

  @Override
  public void requireDepartmentScope(long actorId, Long departmentId) {
    OrganizationApi.Employee actor = administrator(actorId);
    if (!actor.superAdmin() && !Objects.equals(actor.departmentId(), departmentId)) {
      throw BusinessException.forbidden();
    }
  }

  @Override
  public PageResult<OrganizationApi.Employee> employees(
      long actorId, String query, Long departmentId, int page, int size) {
    final var actor = administrator(actorId);
    if (departmentId != null) {
      requireDepartmentScope(actorId, departmentId);
    }
    var found =
        employeeRepository.findAll(
            (root, cq, cb) -> {
              List<Predicate> clauses = new ArrayList<>();
              if (!actor.superAdmin()) {
                clauses.add(cb.equal(root.get("departmentId"), actor.departmentId()));
              }
              if (departmentId != null) {
                clauses.add(cb.equal(root.get("departmentId"), departmentId));
              }
              if (query != null && !query.isBlank()) {
                String text = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
                clauses.add(
                    cb.or(
                        cb.like(root.get("name"), text, '\\'),
                        cb.like(root.get("username"), text, '\\'),
                        cb.like(root.get("phone"), text, '\\')));
              }
              return cb.and(clauses.toArray(Predicate[]::new));
            },
            PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("id")));
    return new PageResult<>(
        found.map(OrganizationService::view).getContent(), found.getTotalElements(), page, size);
  }

  @Override
  public List<OrganizationApi.Company> companies(long actorId) {
    administrator(actorId);
    return companyRepository.findAll().stream()
        .map(c -> new OrganizationApi.Company(c.getId(), c.name, c.active, c.getVersion()))
        .toList();
  }

  @Override
  public List<OrganizationApi.Department> departments(long actorId) {
    final var actor = administrator(actorId);
    return departmentRepository.findAll().stream()
        .filter(d -> actor.superAdmin() || Objects.equals(actor.departmentId(), d.getId()))
        .map(OrganizationService::view)
        .toList();
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Company saveCompany(
      long actorId, Long id, String name, boolean active, long version) {
    requireGlobal(actorId);
    CompanyEntity entity =
        id == null ? new CompanyEntity() : companyRepository.lock(id).orElseThrow();
    if (id != null && entity.getVersion() != version) {
      throw BusinessException.conflict("公司已变化");
    }
    if (!active
        && (employeeRepository.countByCompanyIdAndActiveTrue(id) > 0
            || departmentRepository.findByCompanyId(id).stream().anyMatch(d -> d.active))) {
      throw BusinessException.conflict("请先处理有效员工和部门");
    }
    entity.name = required(name);
    entity.active = active;
    companyRepository.saveAndFlush(entity);
    record(actorId, null, "COMPANY_CHANGED", entity.getId(), "公司资料及状态更新");
    return new OrganizationApi.Company(
        entity.getId(), entity.name, entity.active, entity.getVersion());
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Department saveDepartment(
      long actorId, Long id, Long companyId, String name, boolean active, long version) {
    requireGlobal(actorId);
    CompanyEntity company = companyRepository.lock(companyId).orElseThrow();
    if (!company.active) {
      throw BusinessException.conflict("所属公司已停用");
    }
    DepartmentEntity entity =
        id == null ? new DepartmentEntity() : departmentRepository.findById(id).orElseThrow();
    if (id != null
        && (entity.getVersion() != version || !Objects.equals(entity.companyId, companyId))) {
      throw BusinessException.conflict("部门已变化或不能直接改变所属公司");
    }
    if (!active && employeeRepository.countByDepartmentIdAndActiveTrue(id) > 0) {
      throw BusinessException.conflict("请先处理部门有效员工与管理员");
    }
    entity.companyId = companyId;
    entity.name = required(name);
    entity.active = active;
    departmentRepository.saveAndFlush(entity);
    record(actorId, entity.getId(), "DEPARTMENT_CHANGED", entity.getId(), "部门资料及状态更新");
    return view(entity);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Employee saveEmployee(long actorId, EmployeeChange change) {
    final var actor = administrator(actorId);
    requireDepartmentScope(actorId, change.departmentId());
    EmployeeEntity entity = change.id() == null ? new EmployeeEntity() : lockRecord(change.id());
    if (change.id() != null) {
      requireEmployeeScope(actorId, change.id());
      if (entity.getVersion() != change.version()) {
        throw BusinessException.conflict("员工资料已变化");
      }
      if (entity.departmentId != null
          && !Objects.equals(entity.departmentId, change.departmentId())) {
        throw BusinessException.conflict("部门变更必须先撤权并使用调动流程");
      }
    }
    CompanyEntity company = companyRepository.findById(change.companyId()).orElseThrow();
    if (!company.active) {
      throw BusinessException.conflict("公司已停用");
    }
    if (change.departmentId() != null
        && !Objects.equals(
            activeDepartment(change.departmentId()).companyId(), change.companyId())) {
      throw BusinessException.conflict("公司与部门不匹配");
    }
    if (change.departmentId() == null && !actor.superAdmin()) {
      throw BusinessException.forbidden();
    }
    entity.username = required(change.username());
    entity.name = required(change.name());
    entity.phone = required(change.phone());
    entity.companyId = change.companyId();
    entity.departmentId = change.departmentId();
    entity.positionName = required(change.positionName());
    employeeRepository.saveAndFlush(entity);
    record(actorId, entity.departmentId, "EMPLOYEE_CHANGED", entity.getId(), "员工身份资料更新");
    return view(entity);
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrganizationApi.Employee appoint(
      long actorId, long employeeId, boolean superAdmin, boolean departmentAdmin, long version) {
    requireGlobal(actorId);
    // 固定锁点串行化最高管理员数量检查，避免两人同时取消后无人管理。
    companyRepository.lock(1L).orElseThrow();
    EmployeeEntity entity = lockRecord(employeeId);
    if (entity.getVersion() != version) {
      throw BusinessException.conflict("管理员资料已变化");
    }
    if (entity.superAdmin && !superAdmin && employeeId == actorId) {
      throw BusinessException.conflict("取消本人最高管理员身份须由另一名最高管理员执行");
    }
    if (!entity.active || (departmentAdmin && entity.departmentId == null)) {
      throw BusinessException.conflict("员工停用或部门未确认，不能任命");
    }
    preserveLastAdministrator(entity, superAdmin);
    entity.superAdmin = superAdmin;
    entity.departmentAdmin = departmentAdmin;
    employeeRepository.flush();
    record(actorId, entity.departmentId, "ADMIN_CHANGED", entity.getId(), "管理员身份更新");
    return view(entity);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public OrganizationApi.Employee lockEmployee(long employeeId) {
    if (find(employeeId).superAdmin) {
      companyRepository.lock(1L).orElseThrow();
    }
    return view(lockRecord(employeeId));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void setActive(long actorId, long employeeId, boolean active) {
    requireEmployeeScope(actorId, employeeId);
    EmployeeEntity entity = lockRecord(employeeId);
    if (!active) {
      preserveLastAdministrator(entity, false);
    }
    entity.active = active;
    employeeRepository.flush();
    record(actorId, entity.departmentId, "EMPLOYEE_STATE", employeeId, active ? "员工启用" : "员工停用");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void transfer(long actorId, long employeeId, long departmentId, long expectedVersion) {
    requireGlobal(actorId);
    EmployeeEntity entity = lockRecord(employeeId);
    if (entity.getVersion() != expectedVersion) {
      throw BusinessException.conflict("员工已变化");
    }
    var department = activeDepartment(departmentId);
    final Long old = entity.departmentId;
    entity.departmentId = departmentId;
    entity.companyId = department.companyId();
    entity.departmentAdmin = false;
    employeeRepository.flush();
    audit.record(
        new AuditApi.Event(
            actorId,
            departmentId,
            old,
            null,
            "EMPLOYEE_TRANSFER",
            "employee:" + employeeId,
            "SUCCESS",
            "部门调动，不继承原角色与部门管理员身份"));
  }

  @Override
  public OrganizationApi.Department activeDepartment(long departmentId) {
    DepartmentEntity department = departmentRepository.findById(departmentId).orElseThrow();
    if (!department.active) {
      throw BusinessException.conflict("部门已停用");
    }
    return view(department);
  }

  private void requireGlobal(long actorId) {
    if (!administrator(actorId).superAdmin()) {
      throw BusinessException.forbidden();
    }
  }

  private EmployeeEntity find(long id) {
    return employeeRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(404, "NOT_FOUND", "员工不存在"));
  }

  private EmployeeEntity lockRecord(long id) {
    EmployeeEntity entity = find(id);
    // refresh 同时取得行锁和最新版本，避免范围预读留下一级缓存中的旧状态。
    entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    return entity;
  }

  private void preserveLastAdministrator(EmployeeEntity employee, boolean remainsAdmin) {
    if (employee.superAdmin
        && employee.active
        && !remainsAdmin
        && employeeRepository.countBySuperAdminTrueAndActiveTrue() <= 1) {
      throw BusinessException.conflict("至少保留一名有效最高管理员");
    }
  }

  private void record(long actor, Long department, String type, Long id, String detail) {
    audit.record(
        new AuditApi.Event(
            actor, department, null, null, type, String.valueOf(id), "SUCCESS", detail));
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(400, "INVALID_INPUT", "必填资料不能为空");
    }
    return value.trim();
  }

  private static OrganizationApi.Employee view(EmployeeEntity e) {
    return new OrganizationApi.Employee(
        e.getId(),
        e.username,
        e.name,
        e.phone,
        e.companyId,
        e.departmentId,
        e.positionName,
        e.active,
        e.superAdmin,
        e.departmentAdmin,
        e.getVersion());
  }

  private static OrganizationApi.Department view(DepartmentEntity d) {
    return new OrganizationApi.Department(d.getId(), d.companyId, d.name, d.active, d.getVersion());
  }
}
