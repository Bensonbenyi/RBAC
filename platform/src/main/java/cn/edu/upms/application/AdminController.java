package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.authorization.api.BatchApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import cn.edu.upms.shared.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 将后台 HTTP 请求适配成六个业务模块的公开操作，并组织少量跨模块检查.
 *
 * <p>阅读一条接口时按"Principal 身份 -> 请求 DTO -> 模块用例 -> 响应事实"追踪。 Principal 由安全框架提供；请求中员工编号表示目标对象，不能充当操作者身份。
 * actor 每次复核会话与管理员资格，具体模块还会检查目标范围及并发版本.
 *
 * <p>DTO 是 HTTP 输入输出的形状，record 是模块事实，JPA 实体只存在各模块内部，三者不必一一对应。例如 SystemView
 * 主动去掉凭据哈希。一般方法只转换参数；部门停用和角色删除还需组合其他模块的条件，因此这里显式开启事务并先取得治理锁.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
  /** 公司资料输入. */
  public record CompanyInput(Long id, String name, boolean active, long version) {}

  /** 部门资料输入. */
  public record DepartmentInput(
      Long id, Long companyId, String name, boolean active, long version) {}

  /** 管理员固定身份输入. */
  public record AdministratorInput(
      long employeeId, boolean superAdmin, boolean departmentAdmin, long version) {}

  /** 员工状态输入，启用不恢复旧授权. */
  public record StateInput(boolean active, long version) {}

  /** 人工调动输入. */
  public record TransferInput(long departmentId, long version) {}

  /** 部门可用系统范围替换输入. */
  public record BindInput(Set<Long> departments, long version) {}

  /** 模板复制输入. */
  public record CopyInput(long templateId, long departmentId, String name) {}

  /**
   * 员工角色分配请求，只表达目标与用户确认的旧状态.
   *
   * @param employeeId 被管理员工编号；操作者从 Principal 另行取得
   * @param systemId 目标系统编号
   * @param roleId 新角色编号；{@code null} 代表撤销
   * @param expectedVersion 上次读取的分配版本，首次分配为 {@code -1}
   */
  public record AssignmentInput(
      long employeeId, long systemId, Long roleId, long expectedVersion) {}

  /** 后台系统视图，不包含客户端凭据. */
  public record SystemView(
      long id,
      String key,
      String name,
      boolean active,
      String redirectUri,
      String logoutRedirectUri,
      long directoryRevision,
      long version,
      Set<Long> departments) {}

  private final IdentityApi identity;
  private final OrganizationApi organization;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuthorizationApi authorization;
  private final BatchApi batches;
  private final AuditApi audit;
  private final EmployeeLifecycle lifecycle;

  /** 注入协调所需公开能力，不持有 Repository 或持久化实体. */
  public AdminController(
      IdentityApi identity,
      OrganizationApi organization,
      IntegrationApi integration,
      CatalogApi catalog,
      AuthorizationApi authorization,
      BatchApi batches,
      AuditApi audit,
      EmployeeLifecycle lifecycle) {
    this.identity = identity;
    this.organization = organization;
    this.integration = integration;
    this.catalog = catalog;
    this.authorization = authorization;
    this.batches = batches;
    this.audit = audit;
    this.lifecycle = lifecycle;
  }

  /** 当前后台身份，普通员工不能取得后台资料. */
  @GetMapping("/me")
  public OrganizationApi.Employee me(Principal principal) {
    return organization.administrator(actor(principal));
  }

  /** 只对最高管理员显示全局统计. */
  @GetMapping("/statistics")
  public Map<String, Object> statistics(Principal principal) {
    if (!organization.administrator(actor(principal)).superAdmin()) {
      throw BusinessException.forbidden();
    }
    return audit.statistics();
  }

  /** 查询组织公司. */
  @GetMapping("/companies")
  public List<OrganizationApi.Company> companies(Principal principal) {
    return organization.companies(actor(principal));
  }

  /** 更新公司资料，停用要求无有效组织关联. */
  @PostMapping("/companies")
  public OrganizationApi.Company company(Principal principal, @RequestBody CompanyInput request) {
    return organization.saveCompany(
        actor(principal), request.id(), request.name(), request.active(), request.version());
  }

  /** 查询范围内末级部门. */
  @GetMapping("/departments")
  public List<OrganizationApi.Department> departments(Principal principal) {
    return organization.departments(actor(principal));
  }

  /** 保存部门资料；取消状态前检查接入模块拥有的关联. */
  @PostMapping("/departments")
  @org.springframework.transaction.annotation.Transactional
  public OrganizationApi.Department department(
      Principal principal, @RequestBody DepartmentInput request) {
    // 范围预览可能已过时，检查关联与停用写入必须在同一个治理锁、同一个事务内完成。
    long actorId = actor(principal);
    organization.lockManagement(actorId);
    if (!request.active()
        && request.id() != null
        && integration.systems(actorId).stream()
            .anyMatch(s -> s.departments().contains(request.id()))) {
      throw BusinessException.conflict("请先取消部门可用系统范围");
    }
    return organization.saveDepartment(
        actorId,
        request.id(),
        request.companyId(),
        request.name(),
        request.active(),
        request.version());
  }

  /** 分页筛选员工，服务端强制管理范围. */
  @GetMapping("/employees")
  public PageResult<OrganizationApi.Employee> employees(
      Principal principal,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return organization.employees(actor(principal), query, departmentId, page, size);
  }

  /** 员工详情须在读取前核验所属范围. */
  @GetMapping("/employees/{id}")
  public OrganizationApi.Employee employee(Principal principal, @PathVariable long id) {
    organization.requireEmployeeScope(actor(principal), id);
    return organization.employee(id);
  }

  /** 新建或维护员工资料，部门调动单独执行. */
  @PostMapping("/employees")
  public OrganizationApi.Employee employee(
      Principal principal, @RequestBody OrganizationApi.EmployeeChange request) {
    return organization.saveEmployee(actor(principal), request);
  }

  /** 停用原子撤权及审计，提交后统一会话失效. */
  @PostMapping("/employees/{id}/state")
  public OrganizationApi.Employee state(
      Principal principal, @PathVariable long id, @RequestBody StateInput request) {
    return lifecycle.state(actor(principal), id, request.active(), request.version());
  }

  /** 调动前要求手工撤权并完成生效窗口. */
  @PostMapping("/employees/{id}/transfer")
  public OrganizationApi.Employee transfer(
      Principal principal, @PathVariable long id, @RequestBody TransferInput request) {
    return lifecycle.transfer(actor(principal), id, request.departmentId(), request.version());
  }

  /** 任命或取消固定后台身份，不影响业务角色. */
  @PostMapping("/administrators")
  public OrganizationApi.Employee administrators(
      Principal principal, @RequestBody AdministratorInput request) {
    return organization.appoint(
        actor(principal),
        request.employeeId(),
        request.superAdmin(),
        request.departmentAdmin(),
        request.version());
  }

  /** 查询系统接入和可用范围，凭据不返回前端. */
  @GetMapping("/systems")
  public List<SystemView> systems(Principal principal) {
    return integration.systems(actor(principal)).stream().map(AdminController::systemView).toList();
  }

  /** 最高管理员登记接入资料及精确回调. */
  @PostMapping("/systems")
  public SystemView system(Principal principal, @RequestBody IntegrationApi.SystemChange request) {
    return systemView(integration.save(actor(principal), request));
  }

  /** 明确替换可用部门集合；移除范围影响后续权限判断. */
  @PostMapping("/systems/{id}/departments")
  public SystemView bind(
      Principal principal, @PathVariable long id, @RequestBody BindInput request) {
    return systemView(
        integration.bind(actor(principal), id, request.departments(), request.version()));
  }

  /** 明确重新签发凭据，仅该响应含明文；浏览器禁止缓存. */
  @PostMapping("/systems/{id}/credentials")
  public IntegrationApi.Credentials credentials(
      Principal principal,
      @PathVariable long id,
      @RequestBody Map<String, Long> request,
      HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    Long version = request.get("version");
    if (version == null) {
      throw new BusinessException(400, "INVALID_INPUT", "缺少确认版本");
    }
    return integration.rotateCredentials(actor(principal), id, version);
  }

  /** 管理员只能查看可见系统目录. */
  @GetMapping("/systems/{id}/permissions")
  public List<CatalogApi.Permission> permissions(Principal principal, @PathVariable long id) {
    long actorId = actor(principal);
    if (integration.systems(actorId).stream().noneMatch(s -> s.id() == id)) {
      throw BusinessException.forbidden();
    }
    return catalog.permissions(id);
  }

  /** 筛选全局模板与部门角色. */
  @GetMapping("/roles")
  public List<CatalogApi.Role> roles(
      Principal principal,
      @RequestParam(required = false) Long systemId,
      @RequestParam(required = false) Long departmentId) {
    return catalog.roles(actor(principal), systemId, departmentId);
  }

  /** 修改角色时权限集合显式替换，新增目录项不自动授予. */
  @PostMapping("/roles")
  public CatalogApi.Role role(Principal principal, @RequestBody CatalogApi.RoleChange request) {
    return catalog.save(actor(principal), request);
  }

  /** 治理锁内核对全部未撤销分配，再逻辑删除角色，避免分配与删除交错. */
  @PostMapping("/roles/{id}/delete")
  @org.springframework.transaction.annotation.Transactional
  public Map<String, Boolean> deleteRole(
      Principal principal, @PathVariable long id, @RequestBody Map<String, Long> request) {
    // 授权模块回答"是否仍有人持有"，目录模块执行删除；上层组合，两个模块不相互访问存储。
    // 持有同一治理锁，防止影响查询之后又有并发分配进入。
    long actorId = actor(principal);
    organization.lockManagement(actorId);
    if (!authorization.affected(actorId, id).isEmpty()) {
      throw BusinessException.conflict("请先撤销全部员工分配，停用角色的分配也须撤销");
    }
    Long version = request.get("version");
    if (version == null) {
      throw new BusinessException(400, "INVALID_INPUT", "缺少角色确认版本");
    }
    catalog.delete(actorId, id, version);
    return Map.of("deleted", true);
  }

  /** 复制全局模板为独立部门角色. */
  @PostMapping("/roles/copy")
  public CatalogApi.Role copy(Principal principal, @RequestBody CopyInput request) {
    return catalog.copy(
        actor(principal), request.templateId(), request.departmentId(), request.name());
  }

  /** 角色影响预览，供界面明确停用及权限修改影响. */
  @GetMapping("/roles/{id}/affected")
  public List<Long> affected(Principal principal, @PathVariable long id) {
    return authorization.affected(actor(principal), id);
  }

  /** 查询员工全部系统有效角色、权限与拒绝原因. */
  @GetMapping("/employees/{id}/permissions")
  public List<AuthorizationApi.Effective> employeePermissions(
      Principal principal, @PathVariable long id) {
    return authorization.employeePermissions(actor(principal), id);
  }

  /** 单角色写入使用前端确认的旧版本，避免并发静默覆盖. */
  @PostMapping("/assignments")
  public AuthorizationApi.Assignment assignment(
      Principal principal, @RequestBody AssignmentInput request) {
    return authorization.change(
        actor(principal),
        request.employeeId(),
        request.systemId(),
        request.roleId(),
        request.expectedVersion());
  }

  /** 批量任务先检查整批管理范围，再逐项保存结果. */
  @PostMapping("/batches")
  public BatchApi.Job batch(Principal principal, @RequestBody BatchApi.Request request) {
    return batches.submit(actor(principal), request);
  }

  /** 查询任务及逐项结果. */
  @GetMapping("/batches")
  public List<BatchApi.Job> batches(Principal principal) {
    return batches.jobs(actor(principal));
  }

  /** 仅重试用户重新确认的失败或中断项目. */
  @PostMapping("/batches/{id}/retry")
  public BatchApi.Job retry(
      Principal principal, @PathVariable long id, @RequestBody List<BatchApi.Target> request) {
    return batches.retry(actor(principal), id, request);
  }

  /** 按管理范围、时间与结果筛选审计. */
  @GetMapping("/audit")
  public PageResult<AuditApi.View> audit(
      Principal principal,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return audit.query(
        logScope(actor(principal), departmentId), eventType, result, query, from, to, page, size);
  }

  /** 导出筛选范围全部日志；文本 CSV 单元格转义避免表格公式执行. */
  @GetMapping("/audit/export")
  public void export(
      Principal principal,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      HttpServletResponse response)
      throws IOException {
    // 先从可信会话识别操作者，再计算实际可导出的部门范围。
    long actorId = actor(principal);
    Long scope = logScope(actorId, departmentId);
    // 输出文件前记录导出事件；这表示开始导出，不保证后续网络传输一定完成。
    audit.record(
        new AuditApi.Event(
            actorId,
            scope,
            null,
            null,
            "AUDIT_EXPORTED",
            "audit-csv",
            "SUCCESS",
            "按已校验管理范围导出筛选日志"));
    // 明确 CSV 编码和下载文件名，让浏览器按附件接收。
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=upms-audit.csv");
    // 取得响应流，表头前的 BOM 便于常见表格工具识别中文 UTF-8。
    var writer = response.getWriter();
    writer.write("\uFEFF时间,操作者,部门,系统,事件,对象,结果,说明\n");
    // 每次最多读取 100 条并直接写响应，避免一次把所有历史加载进内存。
    for (int page = 0; ; page++) {
      // 每页沿用同一已校验范围和筛选；当前实现按页读取，不是跨页数据库快照。
      var records = audit.query(scope, eventType, result, query, from, to, page, 100);
      // 逐条输出事件，所有单元格都经 csv 转义，避免公式执行和分隔符错列。
      for (var event : records.items()) {
        writer.write(
            String.join(
                    ",",
                    // 先输出时间和操作者，空引用由同一转换函数处理。
                    csv(event.occurredAt()),
                    csv(event.actorId()),
                    // 部门与系统分别定位管理范围和外部业务范围。
                    csv(event.departmentId()),
                    csv(event.systemId()),
                    // 事件类型与对象引用说明操作及目标，不输出密钥或令牌。
                    csv(event.eventType()),
                    csv(event.objectRef()),
                    // 结果和说明完成本行，末尾换行将事件彼此分开。
                    csv(event.result()),
                    csv(event.detail()))
                + "\n");
      }
      // 已读取条数达到当前查询总数时结束，包括第一页就没有数据的情况。
      if ((page + 1L) * 100 >= records.total()) {
        break;
      }
    }
  }

  /**
   * 从可信会话提取操作者，并检查最新管理员身份；客户端不能自行提交 actorId.
   *
   * <p>这里只确认"谁在操作"，不代表该管理员对任意目标有权；目标范围由后续用例校验.
   */
  private long actor(Principal principal) {
    audit.requireAvailable();
    long actorId = identity.browser(principal).employeeId();
    organization.administrator(actorId);
    return actorId;
  }

  /** 部门管理员不能用省略参数获得全局日志；null 的全局含义只对最高管理员开放. */
  private Long logScope(long actorId, Long requested) {
    var actor = organization.administrator(actorId);
    if (requested != null) {
      organization.requireDepartmentScope(actorId, requested);
    }
    return actor.superAdmin() ? requested : actor.departmentId();
  }

  /** 构造允许发给浏览器的字段白名单，避免完整 SystemFact 中的凭据哈希泄漏. */
  private static SystemView systemView(IntegrationApi.SystemFact s) {
    return new SystemView(
        s.id(),
        s.key(),
        s.name(),
        s.active(),
        s.redirectUri(),
        s.logoutRedirectUri(),
        s.directoryRevision(),
        s.version(),
        s.departments());
  }

  /**
   * 转义 CSV 引号，并让可能被电子表格当作公式的文本按普通文字打开.
   *
   * <p>CSV 双引号转义只保证格式正确，不能阻止以 =、+、-、@ 开头的公式被执行，故另加单引号.
   */
  private static String csv(Object value) {
    String text = value == null ? "" : value.toString();
    if (text.matches("^[=+@\\-].*")) {
      text = "'" + text;
    }
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
