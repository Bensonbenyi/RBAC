package cn.edu.upms.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.integration.api.IntegrationApi.SystemChange;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;

/** 接入契约的输入边界与范围替换；事务及真实约束另由 MySQL 集成测试覆盖. */
class IntegrationServiceTest {
  final SystemRepository systems = mock(SystemRepository.class);
  final LinkRepository links = mock(LinkRepository.class);
  final OrganizationApi organization = mock(OrganizationApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final IntegrationService service = new IntegrationService(systems, links, organization, audit);
  final SystemEntity system = new SystemEntity();
  final List<SystemDepartment> bindings = new ArrayList<>();

  @BeforeEach
  void fixtures() {
    ReflectionTestUtils.setField(system, "id", 1L);
    system.systemKey = "oa";
    system.name = "办公系统";
    when(systems.lock(1L)).thenReturn(Optional.of(system));
    when(systems.findById(1L)).thenReturn(Optional.of(system));
    when(systems.findBySystemKey("oa")).thenReturn(Optional.of(system));
    when(systems.findAll()).thenReturn(List.of(system));
    when(links.findBySystemId(anyLong())).thenReturn(bindings);
    when(organization.administrator(1)).thenReturn(employee(true, 1L));
    when(organization.administrator(2)).thenReturn(employee(false, 2L));
    when(systems.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              SystemEntity entity = call.getArgument(0);
              if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", 2L);
              }
              return entity;
            });
  }

  @Test
  @DisplayName("ACC-F-008 系统可见性、停用和未绑定分别判断")
  void visibilityAndAvailability() {
    var link = new SystemDepartment();
    link.departmentId = 2L;
    bindings.add(link);
    assertThat(service.systems(1)).hasSize(1);
    assertThat(service.systems(2)).hasSize(1);
    link.active = false;
    assertThat(service.systems(2)).isEmpty();
    assertThat(service.available(1, null)).isFalse();
    assertThat(service.available(1, 2L)).isFalse();
    when(links.existsBySystemIdAndDepartmentIdAndActiveTrue(1, 2L)).thenReturn(true);
    assertThat(service.available(1, 2L)).isTrue();
    system.active = false;
    assertThat(service.available(1, 2L)).isFalse();
    assertThatThrownBy(() -> service.byKey("missing")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.system(999)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-008 系统标识及回调校验，公网 HTTP 不允许")
  void validateChanges() {
    for (String key : new String[] {null, "!", "oa-directory"}) {
      reject(new SystemChange(null, key, "名称", true, null, null, 0));
    }
    for (String name : new String[] {null, " "}) {
      reject(new SystemChange(null, "valid", name, true, null, null, 0));
    }
    for (String callback :
        List.of(
            "http://[",
            "relative",
            "https://u:p@example.com/cb",
            "https://example.com/cb#fragment",
            "http://example.com/cb",
            "ftp://localhost/cb")) {
      reject(new SystemChange(null, "valid", "名称", true, callback, null, 0));
    }
    for (String callback :
        new String[] {
          null, " ", "https://example.com/cb", "http://localhost/cb", "http://127.0.0.1/cb"
        }) {
      assertThat(
              service.save(1, new SystemChange(null, "valid", "名称", true, callback, null, 0)).key())
          .isEqualTo("valid");
    }
    reject(new SystemChange(1L, "oa", "名称", true, null, null, 1));
    reject(new SystemChange(1L, "changed", "名称", true, null, null, 0));
    assertThat(service.save(1, new SystemChange(1L, "oa", "新名称", false, null, null, 0)).active())
        .isFalse();
    assertThatThrownBy(
            () -> service.save(2, new SystemChange(null, "valid", "名称", true, null, null, 0)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-008 范围替换保留历史关联，凭据独立签发且仅保存哈希")
  void bindingsAndCredentials() {
    var old = new SystemDepartment();
    old.departmentId = 1L;
    bindings.add(old);
    service.bind(1, 1, Set.of(2L), 0);
    assertThat(old.active).isFalse();
    verify(links).save(any(SystemDepartment.class));
    service.bind(1, 1, Set.of(1L), 0);
    assertThat(old.active).isTrue();
    assertThatThrownBy(() -> service.bind(1, 1, Set.of(), 1)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.rotateCredentials(1, 1, 1))
        .isInstanceOf(BusinessException.class);
    var credentials = service.rotateCredentials(1, 1, 0);
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    assertThat(encoder.matches(credentials.clientSecret(), system.clientSecret)).isTrue();
    assertThat(encoder.matches(credentials.directorySecret(), system.directorySecret)).isTrue();
    assertThat(credentials.directoryClientId()).isEqualTo("oa-directory");
    assertThat(credentials.clientSecret()).isNotEqualTo(credentials.directorySecret());
    assertThat(service.lock(1).key()).isEqualTo("oa");
    service.advanceDirectory(1, 2);
    assertThat(service.byKey("oa").directoryRevision()).isEqualTo(2);
    assertThatThrownBy(() -> service.advanceDirectory(1, 2)).isInstanceOf(BusinessException.class);
  }

  private void reject(SystemChange change) {
    assertThatThrownBy(() -> service.save(1, change)).isInstanceOf(BusinessException.class);
  }

  private static OrganizationApi.Employee employee(boolean global, Long department) {
    return new OrganizationApi.Employee(
        1L, "admin", "管理员", "13800000000", 1L, department, "管理员", true, global, !global, 0);
  }
}
