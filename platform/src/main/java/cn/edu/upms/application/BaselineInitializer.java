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

/**
 * 启动时读取教师 Excel，并协调各模块一次性导入基础资料.
 *
 * <p>ApplicationRunner 在 Spring 启动阶段调用；Order=10 让基线先于演示、压测叠加执行。解析与校验在事务外完成，数据库写入、目录模板、seed_marker
 * 和成功审计在同一事务内提交。 marker 随数据一起提交，失败回滚后下次启动可重新导入，不会把半份数据标成成功.
 *
 * <p>baseline 只保存来源事实，不根据岗位授予管理身份或业务角色；demo、load 的额外配置由后面的独立初始化器执行。这个启动入口不提供运行期 HTTP 导入能力.
 */
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
    // none 模式完全跳过初始化，适用于已有独立测试或部署数据。
    if (seed.equals("none")) {
      return;
    }
    // 显式限制模式名称，拼写错误不能静默降级成意料之外的数据集。
    if (!java.util.Set.of("baseline", "demo", "load").contains(seed)) {
      throw new IllegalStateException("未知初始化模式：" + seed);
    }
    // 用持久化 marker 判断基线是否已完成，重复启动不会重复导入。
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM seed_marker WHERE marker_key='baseline-v1'", Long.class)
        == 0) {
      // 先只读验证全部来源，再持有数据库事务；不要在长事务中等待文件解析。
      var source = TeacherSourceReader.read(directory);
      // 全部解析成功后才打开写事务，各模块导入共享这一提交边界。
      transaction.executeWithoutResult(
          status -> {
            // 按组织、系统、目录的依赖顺序写入，避免引用尚不存在的对象。
            organization.load(source.companies(), source.departments(), source.employees());
            integration.load(
                source.systems().stream()
                    // 系统登记只取编号、标识和名称，权限及模板交给目录模块处理。
                    .map(s -> new IntegrationImportApi.SystemRow(s.id(), s.key(), s.name()))
                    .toList());
            // 逐系统接收初始完整目录，再用其已生成编号创建模板。
            for (var system : source.systems()) {
              catalog.report(system.id(), 1, system.permissions());
              // 模板引用的 key 必须都能在刚导入的目录中定位，否则整个事务回滚。
              catalogImport.templates(system.id(), system.templates());
            }
            // marker 也在同一事务保存，失败后不会留下虚假的已完成标记。
            jdbc.update(
                "INSERT INTO seed_marker(marker_key,applied_at) VALUES ('baseline-v1',NOW(6))");
            // 基础导入成功事件一起提交，审计失败同样不能留下半套初始化数据。
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
    // 输出最终数量和模式，便于确认运行的是基础、演示还是压测数据。
    logger.info("基础资料数量：{}；初始化模式：{}", organization.counts(), seed);
  }
}
