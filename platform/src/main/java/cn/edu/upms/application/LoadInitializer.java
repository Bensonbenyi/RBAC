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
    // 非 load 模式不生成压测配置和输入文件。
    if (!seed.equals("load")) {
      return;
    }
    // 额外核验实际连接的数据库名，避免误把演示库改成批量压测权限。
    if (!"upms_load".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) {
      throw new IllegalStateException("load 模式只允许项目专用 upms_load 数据库");
    }
    // 初始化 marker 不存在时才执行授权叠加，重复启动只重新生成输入文件。
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM seed_marker WHERE marker_key='load-v1'", Long.class)
        == 0) {
      // 压测身份、角色和分配共享事务，失败不会留下部分授权。
      transaction.executeWithoutResult(status -> initialize());
    }
    // 生成的是专用压测输入，不修改教师 Excel；不包含任何运行期令牌。
    Path output = Path.of(".runtime/load");
    // 输出放在忽略的运行目录，专用 CSV 不改来源 Excel。
    Files.createDirectories(output);
    var accounts = allEmployees();
    // 构造固定表头，确保 Gatling feeder 按列名获取账号事实。
    var lines = new ArrayList<String>();
    lines.add("employeeId,username,phone,expectedAllowed");
    // 有确认部门的账号预期允许，54 名待确认账号明确预期拒绝。
    accounts.forEach(
        e ->
            lines.add(
                e.id() + "," + e.username() + "," + e.phone() + "," + (e.departmentId() != null)));
    // 写入全量账号，再生成仅包含 100 名专用管理员的输入。
    Files.write(output.resolve("accounts.csv"), lines, StandardCharsets.UTF_8);
    Files.write(
        output.resolve("admins.csv"),
        // 管理员 CSV 保留同样表头，并从全量账号行中选出指定编号区间。
        lines.stream()
            .filter(
                line ->
                    line.startsWith("employeeId")
                        // 使用编号加逗号匹配完整首列，避免把相似前缀账号误选进来。
                        || IntStream.rangeClosed(1000, 1099)
                            .anyMatch(id -> line.startsWith(id + ",")))
            .toList(),
        StandardCharsets.UTF_8);
  }

  private void initialize() {
    // 专门选择 100 个固定账号作为压测最高管理员，供管理负载使用。
    var admins = IntStream.rangeClosed(1000, 1099).mapToObj(i -> "SG%06d".formatted(i)).toList();
    importer.demonstrationAdministrators(admins, List.of());
    // 建立部门到角色的映射，后续员工只能取本部门对应角色。
    Map<Long, Long> roleByDepartment = new HashMap<>();
    var system = integration.byKey("oa");
    // 读取全部部门编号，显式配置 OA 在所有这些部门可用。
    var departments =
        organization.departments(1000).stream()
            .map(OrganizationApi.Department::id)
            .collect(java.util.stream.Collectors.toSet());
    // 使用系统当前版本替换范围，仍走正常系统接入用例。
    integration.bind(1000, system.id(), departments, system.version());
    // 选择当前系统普通员工模板，后续每部门分别复制。
    var template =
        catalog.roles(1000, system.id(), null).stream()
            .filter(r -> r.template() && r.name().equals("普通员工"))
            .findFirst()
            .orElseThrow();
    // 部门角色互相独立，即使源自同一模板也不能跨部门分配。
    for (long departmentId : departments) {
      roleByDepartment.put(
          departmentId, catalog.copy(1000, template.id(), departmentId, "压测专用-普通员工").id());
    }
    // 批量装载专用分配；待确认部门员工不参与，继续保留拒绝样本。
    assignments.load(
        allEmployees().stream()
            .filter(e -> e.departmentId() != null)
            // 员工、OA 系统和本部门角色三者构成一条初始分配事实。
            .map(
                e ->
                    new AuthorizationImportApi.Row(
                        e.id(), system.id(), roleByDepartment.get(e.departmentId())))
            .toList());
    // 所有专用授权装载完成后才保存 marker。
    jdbc.update("INSERT INTO seed_marker(marker_key,applied_at) VALUES ('load-v1',NOW(6))");
    // 记录压测叠加数量与预期拒绝样本，避免把拒绝错误计成技术故障。
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
