package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationImportApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 构造专用压测夹具与 CSV 输入，不改变教师来源和正常演示数据库.
 *
 * <p>只有 seed=load 且实际数据库名为 upms_load 才能执行。夹具给 100 名账号明确管理身份，给已确认部门员工配置 OA 角色；54
 * 名待确认员工继续作为预期拒绝样本，不能为了压测全通过而给他们补造部门。CSV 中 expectedAllowed 是正确性断言依据，不是平台判断的输入.
 */
@Component
@Order(30)
public class LoadInitializer implements ApplicationRunner {
  private final String seed;
  private final OrganizationApi organization;
  private final OrganizationImportApi importer;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuthorizationImportApi assignments;
  private final AuditApi audit;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  /** 专用模式和数据库名双重限制，避免压测叠加污染演示数据. */
  public LoadInitializer(
      @Value("${upms.seed}") String seed,
      OrganizationApi organization,
      OrganizationImportApi importer,
      IntegrationApi integration,
      CatalogApi catalog,
      AuthorizationImportApi assignments,
      AuditApi audit,
      JdbcTemplate jdbc,
      TransactionTemplate transaction) {
    this.seed = seed;
    this.organization = organization;
    this.importer = importer;
    this.integration = integration;
    this.catalog = catalog;
    this.assignments = assignments;
    this.audit = audit;
    this.jdbc = jdbc;
    this.transaction = transaction;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    if (!seed.equals("load")) {
      return;
    }
    if (!"upms_load".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) {
      throw new IllegalStateException("load 模式只允许项目专用 upms_load 数据库");
    }
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM seed_marker WHERE marker_key='load-v1'", Long.class)
        == 0) {
      transaction.executeWithoutResult(status -> initialize());
    }
    // 生成的是专用压测输入，不修改教师 Excel；不包含任何运行期令牌。
    Path output = Path.of(".runtime/load");
    Files.createDirectories(output);
    var accounts = allEmployees();
    var lines = new ArrayList<String>();
    lines.add("employeeId,username,phone,expectedAllowed");
    accounts.forEach(
        e ->
            lines.add(
                e.id() + "," + e.username() + "," + e.phone() + "," + (e.departmentId() != null)));
    Files.write(output.resolve("accounts.csv"), lines, StandardCharsets.UTF_8);
    Files.write(
        output.resolve("admins.csv"),
        lines.stream()
            .filter(
                line ->
                    line.startsWith("employeeId")
                        || IntStream.rangeClosed(1000, 1099)
                            .anyMatch(id -> line.startsWith(id + ",")))
            .toList(),
        StandardCharsets.UTF_8);
  }

  private void initialize() {
    var admins = IntStream.rangeClosed(1000, 1099).mapToObj(i -> "SG%06d".formatted(i)).toList();
    importer.demonstrationAdministrators(admins, List.of());
    Map<Long, Long> roleByDepartment = new HashMap<>();
    var system = integration.byKey("oa");
    var departments =
        organization.departments(1000).stream()
            .map(OrganizationApi.Department::id)
            .collect(java.util.stream.Collectors.toSet());
    integration.bind(1000, system.id(), departments, system.version());
    var template =
        catalog.roles(1000, system.id(), null).stream()
            .filter(r -> r.template() && r.name().equals("普通员工"))
            .findFirst()
            .orElseThrow();
    for (long departmentId : departments) {
      roleByDepartment.put(
          departmentId, catalog.copy(1000, template.id(), departmentId, "压测专用-普通员工").id());
    }
    assignments.load(
        allEmployees().stream()
            .filter(e -> e.departmentId() != null)
            .map(
                e ->
                    new AuthorizationImportApi.Row(
                        e.id(), system.id(), roleByDepartment.get(e.departmentId())))
            .toList());
    jdbc.update("INSERT INTO seed_marker(marker_key,applied_at) VALUES ('load-v1',NOW(6))");
    audit.record(
        new AuditApi.Event(
            1000L,
            null,
            null,
            null,
            "LOAD_CONFIGURED",
            "load-v1",
            "SUCCESS",
            "专用压测库：100 名管理员，9946 个有确认部门的员工授权，54 人明确预期拒绝"));
  }

  private List<OrganizationApi.Employee> allEmployees() {
    var result = new ArrayList<OrganizationApi.Employee>();
    for (int page = 0; ; page++) {
      var data = organization.employees(1000, null, null, page, 100);
      result.addAll(data.items());
      if (result.size() >= data.total()) {
        return result;
      }
    }
  }
}
