package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.catalog.api.CatalogImportApi;
import cn.edu.upms.integration.api.IntegrationImportApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 以单次事务装载教师基线；演示身份和授权在另一个用例中叠加. */
@Component
@Order(10)
public class BaselineInitializer implements ApplicationRunner {
  private static final Logger logger = LoggerFactory.getLogger(BaselineInitializer.class);
  private final OrganizationImportApi organization;
  private final IntegrationImportApi integration;
  private final CatalogApi catalog;
  private final CatalogImportApi catalogImport;
  private final AuditApi audit;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final String seed;
  private final Path directory;

  /** 读取显式数据模式，空数据库首次启动时装载基线. */
  public BaselineInitializer(
      OrganizationImportApi organization,
      IntegrationImportApi integration,
      CatalogApi catalog,
      CatalogImportApi catalogImport,
      AuditApi audit,
      JdbcTemplate jdbc,
      TransactionTemplate transaction,
      @Value("${upms.seed}") String seed,
      @Value("${upms.source-directory}") String directory) {
    this.organization = organization;
    this.integration = integration;
    this.catalog = catalog;
    this.catalogImport = catalogImport;
    this.audit = audit;
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.seed = seed;
    this.directory = Path.of(directory);
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    if (seed.equals("none")) {
      return;
    }
    if (!java.util.Set.of("baseline", "demo", "load").contains(seed)) {
      throw new IllegalStateException("未知初始化模式：" + seed);
    }
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM seed_marker WHERE marker_key='baseline-v1'", Long.class)
        == 0) {
      var source = TeacherSourceReader.read(directory);
      transaction.executeWithoutResult(
          status -> {
            organization.load(source.companies(), source.departments(), source.employees());
            integration.load(
                source.systems().stream()
                    .map(s -> new IntegrationImportApi.SystemRow(s.id(), s.key(), s.name()))
                    .toList());
            for (var system : source.systems()) {
              catalog.report(system.id(), 1, system.permissions());
              catalogImport.templates(system.id(), system.templates());
            }
            jdbc.update(
                "INSERT INTO seed_marker(marker_key,applied_at) VALUES ('baseline-v1',NOW(6))");
            audit.record(
                new AuditApi.Event(
                    null,
                    null,
                    null,
                    null,
                    "BASELINE_IMPORTED",
                    "teacher-excel",
                    "SUCCESS",
                    "装载基础资料，不按岗位任命或授权"));
          });
    }
    logger.info("基础资料数量：{}；初始化模式：{}", organization.counts(), seed);
  }
}
