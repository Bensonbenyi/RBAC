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

/**
 * 为两个模拟系统增加可识别的"本人"演示动作，并只选择进明确指定的演示角色.
 *
 * <p>先复制当前完整目录，再用新版本追加演示项；这是完整上报，不能只发送新增一项。目录接收不会自动授予新增权限，所以随后另行显式更新演示角色的选择集。
 * 真正记录所有者检查仍由两个模拟系统执行，平台只识别该功能动作.
 */
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
    // 仅 demo 模式叠加本人动作，独立 marker 防止重复加入。
    if (!seed.equals("demo")
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM seed_marker WHERE marker_key='demo-own-v1'", Long.class)
            != 0) {
      return;
    }
    // 目录追加与角色显式选择作为一次事务，不留下只完成一半的演示配置。
    transaction.executeWithoutResult(
        status -> {
          // 分别处理 OA 和知识库，两个系统使用各自目录和角色。
          for (String key : List.of("oa", "knowledge")) {
            var system = integration.byKey(key);
            // 先复制现有完整目录为可变列表，完整上报不能只发送新增项。
            var items =
                new ArrayList<>(
                    catalog.permissions(system.id()).stream()
                        .map(
                            p ->
                                new CatalogApi.DirectoryItem(
                                    // 复制原 key 和启停状态，保证旧有效项不会因追加而被漏报。
                                    p.key(), p.module(), p.feature(), p.action(), p.active()))
                        .toList());
            // 加入专用演示动作；本人业务数据检查仍由模拟系统按记录所有者执行。
            items.add(
                new CatalogApi.DirectoryItem("demo-own:查看本人", "课程演示扩展", "本人记录校验", "查看本人", true));
            // 使用更高目录版本上报，目录接收本身不会自动把新权限授予角色。
            catalog.report(system.id(), system.directoryRevision() + 1, items);
            // 重新查询新权限的数据库编号，为下一步角色选择建立引用。
            var permission =
                catalog.permissions(system.id()).stream()
                    .filter(p -> p.key().equals("demo-own:查看本人"))
                    .findFirst()
                    .orElseThrow();
            // 仅找到指定的部门演示角色，不改全局模板或其他部门角色。
            var role =
                catalog.roles(1, system.id(), 1L).stream()
                    .filter(r -> !r.template() && r.name().equals("演示-普通员工"))
                    .findFirst()
                    .orElseThrow();
            // 复制现有完整选择再加入新项，避免把原权限整体覆盖丢失。
            var chosen = new HashSet<>(role.permissions());
            chosen.add(permission.id());
            // 通过正常角色保存用例提交，版本和范围规则仍然生效。
            catalog.save(
                1,
                new CatalogApi.RoleChange(
                    // 沿用角色身份和归属，不创建一个同名的新角色。
                    role.id(),
                    role.systemId(),
                    role.departmentId(),
                    // 保留原名称，明确这是有效部门角色而非全局模板。
                    role.name(),
                    false,
                    true,
                    // 以当前角色版本提交完整选择，发现并发编辑时应拒绝。
                    role.version(),
                    chosen));
          }
          // 两个系统均完成后写 marker，随后成功审计也参加当前事务。
          jdbc.update(
              "INSERT INTO seed_marker(marker_key,applied_at) VALUES ('demo-own-v1',NOW(6))");
          // 记录只有演示角色被显式加选，避免误解为新增权限会自动授予。
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
