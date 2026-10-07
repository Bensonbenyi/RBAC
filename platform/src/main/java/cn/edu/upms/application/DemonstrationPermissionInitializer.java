package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 显式演示叠加“本人”动作，保留教师目录，不把新权限自动传播给全部角色. */
@Component
@Order(21)
public class DemonstrationPermissionInitializer implements ApplicationRunner {
  private final String seed;
  private final IntegrationApi integration;
  private final CatalogApi catalog;
  private final AuditApi audit;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  /** 两个模拟系统的演示权限与基础导入分开，baseline、load 模式均不执行. */
  public DemonstrationPermissionInitializer(
      @Value("${upms.seed}") String seed,
      IntegrationApi integration,
      CatalogApi catalog,
      AuditApi audit,
      JdbcTemplate jdbc,
      TransactionTemplate transaction) {
    this.seed = seed;
    this.integration = integration;
    this.catalog = catalog;
    this.audit = audit;
    this.jdbc = jdbc;
    this.transaction = transaction;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (!seed.equals("demo")
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM seed_marker WHERE marker_key='demo-own-v1'", Long.class)
            != 0) {
      return;
    }
    transaction.executeWithoutResult(
        status -> {
          for (String key : List.of("oa", "knowledge")) {
            var system = integration.byKey(key);
            var items =
                new ArrayList<>(
                    catalog.permissions(system.id()).stream()
                        .map(
                            p ->
                                new CatalogApi.DirectoryItem(
                                    p.key(), p.module(), p.feature(), p.action(), p.active()))
                        .toList());
            items.add(
                new CatalogApi.DirectoryItem("demo-own:查看本人", "课程演示扩展", "本人记录校验", "查看本人", true));
            catalog.report(system.id(), system.directoryRevision() + 1, items);
            var permission =
                catalog.permissions(system.id()).stream()
                    .filter(p -> p.key().equals("demo-own:查看本人"))
                    .findFirst()
                    .orElseThrow();
            var role =
                catalog.roles(1, system.id(), 1L).stream()
                    .filter(r -> !r.template() && r.name().equals("演示-普通员工"))
                    .findFirst()
                    .orElseThrow();
            var chosen = new HashSet<>(role.permissions());
            chosen.add(permission.id());
            catalog.save(
                1,
                new CatalogApi.RoleChange(
                    role.id(),
                    role.systemId(),
                    role.departmentId(),
                    role.name(),
                    false,
                    true,
                    role.version(),
                    chosen));
          }
          jdbc.update(
              "INSERT INTO seed_marker(marker_key,applied_at) VALUES ('demo-own-v1',NOW(6))");
          audit.record(
              new AuditApi.Event(
                  1L,
                  1L,
                  null,
                  null,
                  "DEMO_CONFIGURED",
                  "demo-own-v1",
                  "SUCCESS",
                  "仅为两个演示角色显式选择本人权限，外部系统继续负责所有者检查"));
        });
  }
}
