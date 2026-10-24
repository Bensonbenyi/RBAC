package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 演示叠加选择有明确类型和名称的对象，不能误选同名模板或别的部门角色. */
class DemonstrationSelectionTest {
  @Test
  @DisplayName("同名非模板不能替代普通员工模板，新增本人动作仅显式加入指定演示角色")
  void explicitSelection() {
    final var importer = mock(OrganizationImportApi.class);
    var integration = mock(IntegrationApi.class);
    var catalog = mock(CatalogApi.class);
    var authorization = mock(AuthorizationApi.class);
    final var audit = mock(AuditApi.class);
    var jdbc = mock(JdbcTemplate.class);
    var transaction = mock(TransactionTemplate.class);
    when(jdbc.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Long.class)))
        .thenReturn(0L);
    doAnswer(
            call -> {
              Consumer<TransactionStatus> callback = call.getArgument(0);
              callback.accept(mock(TransactionStatus.class));
              return null;
            })
        .when(transaction)
        .executeWithoutResult(any());
    var system =
        new IntegrationApi.SystemFact(
            1L, "oa", "系统", true, "hash", "hash", null, null, 1, 0, Set.of());
    when(integration.byKey(any())).thenReturn(system);
    var template = new CatalogApi.Role(1L, 1L, null, "普通员工", true, true, 0, Set.of(1L));
    var departmentRole = new CatalogApi.Role(2L, 1L, 1L, "演示-普通员工", false, true, 0, Set.of(1L));
    when(catalog.roles(1, 1L, null)).thenReturn(List.of(departmentRole, template));
    when(catalog.copy(1, 1, 1, "演示-普通员工")).thenReturn(departmentRole);
    when(authorization.assignment(any(long.class), any(long.class)))
        .thenReturn(new AuthorizationApi.Assignment(13L, 1L, null, true, -1, null));
    new DemonstrationInitializer(
            importer, integration, catalog, authorization, audit, jdbc, transaction, "demo")
        .run(null);
    verify(authorization, org.mockito.Mockito.times(2)).change(1, 13, 1, 2L, -1);
    when(catalog.roles(1, 1L, 1L))
        .thenReturn(
            List.of(
                template,
                new CatalogApi.Role(3L, 1L, 1L, "其它角色", false, true, 0, Set.of()),
                departmentRole));
    when(catalog.permissions(1))
        .thenReturn(
            List.of(
                new CatalogApi.Permission(3L, 1L, "demo-own:查看本人", "模块", "功能", "查看本人", true, 0)));
    new DemonstrationPermissionInitializer("demo", integration, catalog, audit, jdbc, transaction)
        .run(null);
    var chosen = ArgumentCaptor.forClass(CatalogApi.RoleChange.class);
    verify(catalog, org.mockito.Mockito.times(2))
        .save(org.mockito.ArgumentMatchers.eq(1L), chosen.capture());
    assertThat(chosen.getValue().id()).isEqualTo(2);
    assertThat(chosen.getValue().permissions()).containsExactlyInAnyOrder(1L, 3L);
  }
}
