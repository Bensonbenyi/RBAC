package cn.edu.upms.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 隔离事实读取，验证异常归属及绝对缓存窗口；并发与回滚仍由真实数据库测试证明. */
class AuthorizationServiceTest {
  final AssignmentRepository assignments = mock(AssignmentRepository.class);
  final OrganizationApi organization = mock(OrganizationApi.class);
  final IntegrationApi integration = mock(IntegrationApi.class);
  final CatalogApi catalog = mock(CatalogApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  final AssignmentEntity assignment = new AssignmentEntity();
  final AtomicLong time = new AtomicLong();
  AuthorizationService service;

  @BeforeEach
  void fixtures() {
    service =
        new AuthorizationService(
            assignments, organization, integration, catalog, audit, manager, true, time::get);
    when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    assignment.employeeId = 1L;
    assignment.systemId = 1L;
    assignment.roleId = 2L;
    assignment.revoked = false;
    assignment.changedAt = Instant.now().minusSeconds(10);
    when(assignments.findByEmployeeIdAndSystemId(1, 1)).thenReturn(Optional.of(assignment));
    when(assignments.findByEmployeeId(1)).thenReturn(List.of(assignment));
    when(assignments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    when(organization.employee(1)).thenReturn(employee(true, 1L));
    when(organization.lockEmployee(1)).thenReturn(employee(true, 1L));
    when(organization.activeDepartment(1))
        .thenReturn(new OrganizationApi.Department(1L, 1L, "部门", true, 0));
    when(integration.lock(1)).thenReturn(system(true));
    when(integration.available(1, 1L)).thenReturn(true);
    when(integration.systems(1)).thenReturn(List.of(system(true)));
    when(catalog.role(2)).thenReturn(role(1L, 1L, false, true));
    when(catalog.lock(2)).thenReturn(role(1L, 1L, false, true));
    when(catalog.permissions(1))
        .thenReturn(List.of(permission(1, true), permission(2, false), permission(3, true)));
  }

  @Test
  @DisplayName("ACC-F-008 换角逐一拒绝无效员工、系统、范围、模板和异属角色")
  void invalidAssignmentFacts() {
    rejectChange(1);
    when(organization.lockEmployee(1)).thenReturn(employee(false, 1L));
    rejectChange(0);
    when(organization.lockEmployee(1)).thenReturn(employee(true, 1L));
    when(integration.lock(1)).thenReturn(system(false));
    rejectChange(0);
    when(integration.lock(1)).thenReturn(system(true));
    when(integration.available(1, 1L)).thenReturn(false);
    rejectChange(0);
    when(integration.available(1, 1L)).thenReturn(true);
    for (CatalogApi.Role invalid :
        List.of(
            role(1L, 1L, false, false),
            role(1L, null, true, true),
            role(2L, 1L, false, true),
            role(1L, 2L, false, true))) {
      when(catalog.lock(2)).thenReturn(invalid);
      rejectChange(0);
    }
  }

  @Test
  @DisplayName("ACC-F-014 有效权限读取防御缺失与异属事实；允许与拒绝原因准确")
  void readFactsAndDecisions() {
    assertThat(service.effective(1, 1).permissions()).containsExactly("read1");
    assertThat(service.decide(1, 1, null).reason()).isEqualTo("PERMISSION_MISSING");
    assertThat(service.decide(1, 1, "read1").allowed()).isTrue();
    for (CatalogApi.Role invalid :
        List.of(
            role(1L, 1L, false, false),
            role(1L, null, true, true),
            role(2L, 1L, false, true),
            role(1L, 2L, false, true))) {
      when(catalog.role(2)).thenReturn(invalid);
      assertThat(service.effective(1, 1).permissions()).isEmpty();
    }
    when(catalog.role(2)).thenReturn(null);
    assertThat(service.effective(1, 1).permissions()).isEmpty();
    assignment.roleId = null;
    assertThat(service.effective(1, 1).permissions()).isEmpty();
    assignment.revoked = true;
    when(organization.employee(1)).thenReturn(employee(true, null));
    assertThat(service.effective(1, 1).permissions()).isEmpty();
    when(organization.employee(1)).thenReturn(employee(true, 1L));
    when(organization.activeDepartment(1)).thenThrow(BusinessException.conflict("停用"));
    assertThat(service.effective(1, 1).reason()).isEqualTo("DEPARTMENT_UNAVAILABLE");
  }

  @Test
  @DisplayName("ACC-P-005 命中不续期，过期重新读取，慢读取超出两秒时拒绝")
  void cacheDeadlineAndNoCacheBaseline() {
    assertThat(service.decide(1, 1, "read1").allowed()).isTrue();
    assignment.revoked = true;
    time.set(1_900_000_000L);
    assertThat(service.decide(1, 1, "read1").allowed()).isTrue();
    time.set(2_000_000_000L);
    assertThat(service.decide(1, 1, "read1").allowed()).isFalse();
    var noCache =
        new AuthorizationService(
            assignments, organization, integration, catalog, audit, manager, false);
    assertThat(noCache.decide(1, 1, "read1").allowed()).isFalse();
    time.set(4_000_000_000L);
    when(organization.employee(1))
        .thenAnswer(
            call -> {
              time.addAndGet(2_000_000_000L);
              return employee(true, 1L);
            });
    assertThatThrownBy(() -> service.decide(1, 1, "read1")).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-011/016 撤销保留版本，调动须全部撤权并越过窗口，范围内影响查询")
  void revokeTransferAndReadModels() {
    assertThat(service.employeePermissions(1, 1)).hasSize(1);
    when(assignments.findByRoleIdAndRevokedFalse(2)).thenReturn(List.of(assignment));
    assertThat(service.affected(1, 2)).containsExactly(1L);
    assertThatThrownBy(() -> service.requireTransferReady(1)).isInstanceOf(BusinessException.class);
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.revokeAll(1, 1);
      assertThat(assignment.revoked).isTrue();
      assertThat(assignment.roleId).isNull();
      assertThatThrownBy(() -> service.requireTransferReady(1))
          .isInstanceOf(BusinessException.class);
      service.revokeAll(1, 1);
      assignment.changedAt = Instant.now().minusSeconds(4);
      service.requireTransferReady(1);
      when(assignments.findByEmployeeId(1)).thenReturn(List.of());
      service.requireTransferReady(1);
      TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private void rejectChange(long version) {
    assertThatThrownBy(() -> service.change(1, 1, 1, 2L, version))
        .isInstanceOf(BusinessException.class);
  }

  private static OrganizationApi.Employee employee(boolean active, Long department) {
    return new OrganizationApi.Employee(
        1L, "worker", "员工", "phone", 1L, department, "岗位", active, false, false, 0);
  }

  private static CatalogApi.Role role(
      Long system, Long department, boolean template, boolean active) {
    return new CatalogApi.Role(2L, system, department, "角色", template, active, 0, Set.of(1L, 2L));
  }

  private static CatalogApi.Permission permission(long id, boolean active) {
    return new CatalogApi.Permission(id, 1L, "read" + id, "模块", "功能", "查看", active, 0);
  }

  private static IntegrationApi.SystemFact system(boolean active) {
    return new IntegrationApi.SystemFact(
        1L, "oa", "办公", active, "hash", "hash", null, null, 1, 0, Set.of(1L));
  }
}
