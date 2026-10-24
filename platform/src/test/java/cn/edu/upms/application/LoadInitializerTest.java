package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationImportApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import cn.edu.upms.shared.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 压测夹具生成验证分页、待确认部门和重复运行，不触碰真实 upms_load 数据. */
class LoadInitializerTest {
  @Test
  @DisplayName("压测模式检查数据库名，只生成已确认部门授权并输出独立测试输入")
  void loadFixtureRules() throws Exception {
    var organization = mock(OrganizationApi.class);
    var importer = mock(OrganizationImportApi.class);
    var integration = mock(IntegrationApi.class);
    var catalog = mock(CatalogApi.class);
    var assignments = mock(AuthorizationImportApi.class);
    var audit = mock(AuditApi.class);
    var jdbc = mock(JdbcTemplate.class);
    var transaction = mock(TransactionTemplate.class);
    doAnswer(
            call -> {
              Consumer<TransactionStatus> callback = call.getArgument(0);
              callback.accept(mock(TransactionStatus.class));
              return null;
            })
        .when(transaction)
        .executeWithoutResult(any());
    var initializer =
        new LoadInitializer(
            "load",
            organization,
            importer,
            integration,
            catalog,
            assignments,
            audit,
            jdbc,
            transaction);
    when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("upms");
    assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class);
    when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("upms_load");
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM seed_marker WHERE marker_key='load-v1'", Long.class))
        .thenReturn(0L, 1L);
    when(integration.byKey("oa"))
        .thenReturn(
            new IntegrationApi.SystemFact(
                1L, "oa", "办公", true, "hash", "hash", null, null, 1, 0, Set.of()));
    when(organization.departments(1000))
        .thenReturn(List.of(new OrganizationApi.Department(1L, 1L, "部门", true, 0)));
    var template = new CatalogApi.Role(1L, 1L, null, "普通员工", true, true, 0, Set.of(1L));
    when(catalog.roles(1000, 1L, null))
        .thenReturn(
            List.of(
                new CatalogApi.Role(2L, 1L, 1L, "非模板", false, true, 0, Set.of()),
                new CatalogApi.Role(4L, 1L, null, "其他模板", true, true, 0, Set.of()),
                template));
    when(catalog.copy(1000, 1, 1, "压测专用-普通员工"))
        .thenReturn(new CatalogApi.Role(3L, 1L, 1L, "压测专用-普通员工", false, true, 0, Set.of(1L)));
    when(organization.employees(1000, null, null, 0, 100))
        .thenReturn(new PageResult<>(List.of(employee(1000, 1L), employee(1001, null)), 3, 0, 100));
    when(organization.employees(1000, null, null, 1, 100))
        .thenReturn(new PageResult<>(List.of(employee(2000, 1L)), 3, 1, 100));
    initializer.run(null);
    initializer.run(null);
    var captured = ArgumentCaptor.forClass(List.class);
    verify(assignments).load(captured.capture());
    assertThat(captured.getValue())
        .containsExactly(
            new AuthorizationImportApi.Row(1000, 1, 3), new AuthorizationImportApi.Row(2000, 1, 3));
    assertThat(Files.readString(Path.of(".runtime/load/accounts.csv")))
        .contains("1001,account1001,phone1001,false");
    assertThat(Files.readString(Path.of(".runtime/load/admins.csv")))
        .contains("1000,")
        .doesNotContain("2000,");
  }

  private static OrganizationApi.Employee employee(long id, Long department) {
    return new OrganizationApi.Employee(
        id, "account" + id, "压测员工", "phone" + id, 1L, department, "岗位", true, false, false, 0);
  }
}
