package cn.edu.upms.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import cn.edu.upms.shared.BusinessException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/** 每个竞争分支使用真实独立事务及数据库连接，验证锁与版本而非线程内模拟. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/upms_test?serverTimezone=Asia/Shanghai",
      "upms.seed=baseline",
      "upms.source-directory=../docs"
    })
class AssignmentConcurrencyTest {
  @Autowired private AuthorizationApi authorization;
  @Autowired private OrganizationApi organization;
  @Autowired private OrganizationImportApi importer;
  @Autowired private IntegrationApi integration;
  @Autowired private CatalogApi catalog;
  @Autowired private TransactionTemplate transaction;
  private long systemId;
  private long roleId;

  @BeforeEach
  void prepare() {
    transaction.executeWithoutResult(
        status -> {
          importer.demonstrationAdministrators(List.of("SG000001"), List.of("SG000011"));
          var oa = integration.byKey("oa");
          systemId = oa.id();
          integration.bind(1, systemId, Set.of(1L, 2L), oa.version());
          var role =
              catalog.roles(1, systemId, 1L).stream()
                  .filter(r -> !r.template())
                  .findFirst()
                  .orElseGet(
                      () ->
                          catalog.copy(
                              1,
                              catalog.roles(1, systemId, null).stream()
                                  .filter(CatalogApi.Role::template)
                                  .findFirst()
                                  .orElseThrow()
                                  .id(),
                              1,
                              "并发测试角色"));
          roleId = role.id();
          organization.lockEmployee(13);
          organization.setActive(1, 13, true);
          authorization.revokeAll(1, 13);
        });
  }

  @Test
  @DisplayName("ACC-F-010 两个同版本换角只有一个成功，另一方收到冲突")
  void simultaneousAssignments() throws Exception {
    long version = authorization.assignment(13, systemId).version();
    List<String> outcomes = race(() -> assign(version), () -> assign(version));
    assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "409");
    assertThat(authorization.assignment(13, systemId).roleId()).isEqualTo(roleId);
  }

  @AfterEach
  void restoreEmployee() {
    transaction.executeWithoutResult(
        status -> {
          organization.lockEmployee(13);
          organization.setActive(1, 13, true);
          authorization.revokeAll(1, 13);
        });
  }

  @Test
  @DisplayName("ACC-F-014 员工停用与授权竞争后不会留下有效授权")
  void disableAndAssign() throws Exception {
    long version = authorization.assignment(13, systemId).version();
    race(
        () -> assign(version),
        () -> {
          transaction.executeWithoutResult(
              status -> {
                organization.lockEmployee(13);
                organization.setActive(1, 13, false);
                authorization.revokeAll(1, 13);
              });
          return "SUCCESS";
        });
    assertThat(organization.employee(13).active()).isFalse();
    assertThat(authorization.assignment(13, systemId).revoked()).isTrue();
    assertThat(authorization.effective(13, systemId).permissions()).isEmpty();
  }

  private String assign(long version) {
    try {
      authorization.change(1, 13, systemId, roleId, version);
      return "SUCCESS";
    } catch (BusinessException rejected) {
      return String.valueOf(rejected.getStatus());
    }
  }

  private static List<String> race(Callable<String> first, Callable<String> second)
      throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return first.call();
              });
      var b =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return second.call();
              });
      return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    }
  }
}
