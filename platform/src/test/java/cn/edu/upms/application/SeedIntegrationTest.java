package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import cn.edu.upms.UpmsApplication;
import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.catalog.api.CatalogImportApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.integration.api.IntegrationImportApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 专用随机数据库验证真实 Excel 首次导入和演示叠加，不复用演示、基线或压测库. */
@SpringBootTest(properties = {"upms.seed=demo", "upms.source-directory=../docs"})
@DirtiesContext
class SeedIntegrationTest {
  static final String database = "upms_coverage_" + UUID.randomUUID().toString().replace("-", "");
  @Autowired OrganizationImportApi organization;
  @Autowired IntegrationImportApi integrationImport;
  @Autowired IntegrationApi integration;
  @Autowired CatalogApi catalog;
  @Autowired CatalogImportApi catalogImport;
  @Autowired AuditApi audit;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transaction;
  @Autowired BaselineInitializer baseline;
  @Autowired DemonstrationInitializer demo;
  @Autowired DemonstrationPermissionInitializer own;

  @org.junit.jupiter.api.AfterAll
  static void removeIsolatedDatabase() throws Exception {
    // 只清理由本测试生成的随机库，绝不接受外部数据库名称。
    if (!database.matches("upms_coverage_[a-f0-9]{32}")) {
      throw new IllegalStateException("非测试库");
    }
    try (var connection =
            DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3307/mysql",
                "root",
                System.getenv().getOrDefault("MYSQL_ROOT_PASSWORD", "upms-local-root"));
        var statement = connection.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + database);
      statement.execute("REVOKE ALL ON " + database + ".* FROM 'upms'@'%'");
    }
  }

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry properties) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3307/mysql",
                "root",
                System.getenv().getOrDefault("MYSQL_ROOT_PASSWORD", "upms-local-root"));
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
      statement.execute("GRANT ALL ON " + database + ".* TO 'upms'@'%'");
    }
    properties.add(
        "spring.datasource.url",
        () -> "jdbc:mysql://127.0.0.1:3307/" + database + "?serverTimezone=Asia/Shanghai");
  }

  @Test
  @DisplayName("ACC-F-001 首次初始化完整装载，标记幂等，不允许重复导入覆盖事实")
  void freshDatabaseAndRepeat() throws Exception {
    assertThat(organization.counts())
        .containsEntry("employees", 10000L)
        .containsEntry("unconfirmedDepartments", 54L);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seed_marker", Long.class)).isEqualTo(3);
    assertThat(integration.registered()).hasSize(17);
    for (String key : List.of("oa", "knowledge")) {
      var system = integration.byKey(key);
      assertThat(system.redirectUri()).startsWith("http://localhost:");
      var role =
          catalog.roles(1, system.id(), 1L).stream()
              .filter(r -> !r.template())
              .findFirst()
              .orElseThrow();
      assertThat(role.permissions()).isNotEmpty();
      assertThat(catalog.permissions(system.id())).anyMatch(p -> p.key().equals("demo-own:查看本人"));
    }
    baseline.run(new DefaultApplicationArguments());
    demo.run(new DefaultApplicationArguments());
    own.run(new DefaultApplicationArguments());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seed_marker", Long.class)).isEqualTo(3);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> organization.load(List.of(), List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> transaction.executeWithoutResult(status -> integrationImport.load(List.of())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status ->
                        organization.demonstrationAdministrators(List.of("missing"), List.of())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status ->
                        organization.demonstrationAdministrators(List.of(), List.of("missing"))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> catalogImport.templates(1, Map.of("错误模板", Set.of("missing")))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("初始化关闭模式不读来源，未知模式失败，启动入口传递环境参数")
  void explicitModesAndEntry() throws Exception {
    new BaselineInitializer(
            organization,
            integrationImport,
            catalog,
            catalogImport,
            audit,
            jdbc,
            transaction,
            "none",
            "missing")
        .run(null);
    assertThatThrownBy(
            () ->
                new BaselineInitializer(
                        organization,
                        integrationImport,
                        catalog,
                        catalogImport,
                        audit,
                        jdbc,
                        transaction,
                        "invalid",
                        "missing")
                    .run(null))
        .isInstanceOf(IllegalStateException.class);
    String[] arguments = {"--server.port=0"};
    try (var spring = mockStatic(SpringApplication.class)) {
      UpmsApplication.main(arguments);
      spring.verify(() -> SpringApplication.run(UpmsApplication.class, arguments));
    }
  }
}
