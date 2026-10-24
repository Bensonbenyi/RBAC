package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在教师基线之上显式叠加少量课程演示账号与权限，和纯基础数据分开.
 *
 * <p>Order=20 保证基线已经导入；仅 seed=demo 且 marker 不存在时执行。管理员清单、可用部门、模板复制与员工分配在同一事务里配置，成功后记录
 * marker，重复启动不重复授权。这里的少量固定编号是受控演示夹具，不是运行期按岗位或姓名猜测权限的规则.
 */
@Component
@Order(20)
public class DemonstrationInitializer implements ApplicationRunner {
  private final OrganizationImportApi importer;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuthorizationApi authorization;
  private final AuditApi audit;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final String seed;

  /** 读取显式模式；演示配置在独立事务中可追溯地执行. */
  public DemonstrationInitializer(
      OrganizationImportApi importer,
      IntegrationApi integration,
      CatalogApi catalog,
      AuthorizationApi authorization,
      AuditApi audit,
      JdbcTemplate jdbc,
      TransactionTemplate transaction,
      @Value("${upms.seed}") String seed) {
    this.importer = importer;
    this.integration = integration;
    this.catalog = catalog;
    this.authorization = authorization;
    this.audit = audit;
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.seed = seed;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    // 只在显式 demo 模式且尚无演示 marker 时运行，基础数据模式不叠加权限。
    if (!seed.equals("demo")
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM seed_marker WHERE marker_key='demo-v1'", Long.class)
            != 0) {
      return;
    }
    // 演示任命、系统范围和授权一起提交，任一步失败都能整体重试。
    transaction.executeWithoutResult(
        status -> {
          // 固定用户名来自已标记的演示夹具，不按岗位自动猜测管理员。
          importer.demonstrationAdministrators(
              List.of("SG000001", "SG000002"), List.of("SG000011", "SG000051"));
          // 仅为两个实际运行的模拟系统准备示例，其他系统保留登记数据。
          for (String key : List.of("oa", "knowledge")) {
            var system = integration.byKey(key);
            // 明确开放两个演示部门，版本取自刚读取的系统事实。
            integration.bind(1, system.id(), Set.of(1L, 2L), system.version());
            // 从当前系统中寻找普通员工全局模板，避免拿到其他系统或部门角色。
            var template =
                catalog.roles(1, system.id(), null).stream()
                    .filter(r -> r.template() && r.name().equals("普通员工"))
                    .findFirst()
                    .orElseThrow();
            // 复制为部门一的独立角色，模板后续变化不会自动修改该角色。
            var role = catalog.copy(1, template.id(), 1, "演示-普通员工");
            // 只给两名指定员工配置权限，其他基础员工仍无自动授权。
            for (long employeeId : List.of(13L, 14L)) {
              authorization.change(
                  1,
                  employeeId,
                  system.id(),
                  role.id(),
                  // 读取当前分配版本作为预期值，仍经过正常单角色和范围校验。
                  authorization.assignment(employeeId, system.id()).version());
            }
          }
          // 全部演示配置成功后再写 marker，它与这些变更共同提交。
          jdbc.update("INSERT INTO seed_marker(marker_key,applied_at) VALUES ('demo-v1',NOW(6))");
          // 记录明确的演示叠加范围，便于区分教师来源事实与额外夹具。
          audit.record(
              new AuditApi.Event(
                  1L,
                  null,
                  null,
                  null,
                  "DEMO_CONFIGURED",
                  "demo-v1",
                  "SUCCESS",
                  "演示叠加：两名最高管理员、两名部门管理员、两个系统少量员工授权"));
        });
  }
}
