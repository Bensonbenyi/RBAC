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

/** 单独叠加少量演示管理员与角色，基础模式不会执行；与来源岗位无关. */
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
    if (!seed.equals("demo")
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM seed_marker WHERE marker_key='demo-v1'", Long.class)
            != 0) {
      return;
    }
    transaction.executeWithoutResult(
        status -> {
          importer.demonstrationAdministrators(
              List.of("SG000001", "SG000002"), List.of("SG000011", "SG000051"));
          for (String key : List.of("oa", "knowledge")) {
            var system = integration.byKey(key);
            integration.bind(1, system.id(), Set.of(1L, 2L), system.version());
            var template =
                catalog.roles(1, system.id(), null).stream()
                    .filter(r -> r.template() && r.name().equals("普通员工"))
                    .findFirst()
                    .orElseThrow();
            var role = catalog.copy(1, template.id(), 1, "演示-普通员工");
            for (long employeeId : List.of(13L, 14L)) {
              authorization.change(
                  1,
                  employeeId,
                  system.id(),
                  role.id(),
                  authorization.assignment(employeeId, system.id()).version());
            }
          }
          jdbc.update("INSERT INTO seed_marker(marker_key,applied_at) VALUES ('demo-v1',NOW(6))");
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
