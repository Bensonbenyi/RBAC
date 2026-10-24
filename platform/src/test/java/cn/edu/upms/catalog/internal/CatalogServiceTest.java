package cn.edu.upms.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi.DirectoryItem;
import cn.edu.upms.catalog.api.CatalogApi.RoleChange;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 角色归属、显式权限集与全量目录重报的契约测试. */
class CatalogServiceTest {
  final PermissionRepository permissions = mock(PermissionRepository.class);
  final RoleRepository roles = mock(RoleRepository.class);
  final IntegrationApi integration = mock(IntegrationApi.class);
  final OrganizationApi organization = mock(OrganizationApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final CatalogService service =
      new CatalogService(permissions, roles, integration, organization, audit);
  final List<RoleEntity> roleRows = new ArrayList<>();
  final List<PermissionEntity> permissionRows = new ArrayList<>();
  final RoleEntity role = role(1, 1L, 1L, false, "角色");
  final PermissionEntity permission = new PermissionEntity();

  @BeforeEach
  void fixtures() {
    roleRows.add(role);
    role.permissions.add(1L);
    ReflectionTestUtils.setField(permission, "id", 1L);
    permission.systemId = 1L;
    permission.permissionKey = "read";
    permission.moduleName = "模块";
    permission.featureName = "功能";
    permission.actionName = "查看";
    permissionRows.add(permission);
    when(permissions.findBySystemId(1)).thenReturn(permissionRows);
    when(roles.findAll()).thenReturn(roleRows);
    when(roles.findById(1L)).thenReturn(Optional.of(role));
    when(roles.lock(1L)).thenReturn(Optional.of(role));
    when(roles.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              RoleEntity saved = call.getArgument(0);
              if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 100L);
              }
              return saved;
            });
    when(integration.lock(anyLong())).thenReturn(system(true, 1));
    when(integration.available(anyLong(), any())).thenReturn(true);
    when(organization.administrator(1)).thenReturn(actor(true));
    when(organization.administrator(2)).thenReturn(actor(false));
  }

  @Test
  @DisplayName("ACC-F-009 角色列表同时按系统、部门、模板可用范围及逻辑删除过滤")
  void listScopes() {
    roleRows.add(role(2, 2L, 1L, false, "别的系统"));
    roleRows.add(role(3, 1L, 2L, false, "别的部门"));
    roleRows.add(role(4, 1L, null, true, "模板"));
    var deleted = role(5, 1L, 1L, false, "已删");
    deleted.deleted = true;
    roleRows.add(deleted);
    assertThat(service.roles(1, null, null)).hasSize(4);
    assertThat(service.roles(1, 1L, 1L)).hasSize(2);
    assertThat(service.roles(2, null, null)).hasSize(3);
    when(integration.available(anyLong(), any())).thenReturn(false);
    assertThat(service.roles(2, null, null)).hasSize(2);
    assertThatThrownBy(() -> service.role(999)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("角色保存拒绝错误归属、无效名称及未知权限，历史停用引用可保留")
  void roleRules() {
    reject(new RoleChange(null, 1L, 1L, "新角色", true, true, 0, Set.of()));
    reject(new RoleChange(null, 1L, null, "新角色", false, true, 0, Set.of()));
    when(integration.lock(1)).thenReturn(system(false, 1));
    reject(change(null, "新角色", 0, Set.of()));
    when(integration.lock(1)).thenReturn(system(true, 1));
    when(integration.available(1, 1L)).thenReturn(false);
    reject(change(null, "新角色", 0, Set.of()));
    when(integration.available(1, 1L)).thenReturn(true);
    for (String name : new String[] {null, " ", "长".repeat(121)}) {
      reject(change(null, name, 0, Set.of()));
    }
    reject(change(null, "新角色", 0, Set.of(999L)));
    permission.active = false;
    reject(change(null, "新角色", 0, Set.of(1L)));
    assertThat(service.save(1, change(1L, "角色", 0, Set.of(1L))).permissions()).containsExactly(1L);
    permission.active = true;
    assertThat(service.save(1, change(null, "新角色", 0, Set.of(1L))).template()).isFalse();
    assertThat(
            service
                .save(1, new RoleChange(null, 1L, null, "新模板", true, true, 0, Set.of()))
                .template())
        .isTrue();
  }

  @Test
  @DisplayName("角色更新检测版本、归属及同名冲突")
  void revisionsAndDuplicates() {
    role.deleted = true;
    reject(change(1L, "角色", 0, Set.of()));
    role.deleted = false;
    reject(change(1L, "角色", 1, Set.of()));
    role.departmentId = 2L;
    reject(change(1L, "角色", 0, Set.of()));
    role.departmentId = 1L;
    role.systemId = 2L;
    reject(change(1L, "角色", 0, Set.of()));
    role.systemId = 1L;
    role.template = true;
    reject(change(1L, "角色", 0, Set.of()));
    role.template = false;
    roleRows.add(role(2, 2L, 1L, false, "无关系统"));
    roleRows.add(role(3, 1L, 2L, false, "无关部门"));
    roleRows.add(role(4, 1L, 1L, false, "重复"));
    reject(change(null, "重复", 0, Set.of()));
    assertThat(service.save(1, change(1L, "改名", 0, Set.of())).name()).isEqualTo("改名");
  }

  @Test
  @DisplayName("ACC-F-009 模板复制要求有效模板，角色删除保留历史并检查版本")
  void copyAndDeletion() {
    assertThatThrownBy(() -> service.copy(1, 1, 1, "复制")).isInstanceOf(BusinessException.class);
    role.template = true;
    role.departmentId = null;
    role.active = false;
    assertThatThrownBy(() -> service.copy(1, 1, 1, "复制")).isInstanceOf(BusinessException.class);
    role.active = true;
    assertThat(service.copy(1, 1, 1, "复制").permissions()).containsExactly(1L);
    assertThatThrownBy(() -> service.delete(1, 1, 1)).isInstanceOf(BusinessException.class);
    service.delete(1, 1, 0);
    assertThat(role.deleted).isTrue();
    assertThat(role.active).isFalse();
    assertThat(role.permissions).isEmpty();
    assertThatThrownBy(() -> service.delete(1, 1, 0)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-013 目录状态、规模和每个文本边界均校验后再改变事实")
  void invalidDirectories() {
    when(integration.lock(1)).thenReturn(system(false, 1));
    rejectReport(2, List.of());
    when(integration.lock(1)).thenReturn(system(true, 2));
    rejectReport(1, List.of());
    when(integration.lock(1)).thenReturn(system(true, 0));
    rejectReport(0, List.of());
    rejectReport(1, null);
    rejectReport(1, Collections.nCopies(10001, item("read", true)));
    rejectReport(1, Arrays.asList((DirectoryItem) null));
    for (String text : new String[] {null, " ", "长".repeat(161)}) {
      rejectReport(1, List.of(new DirectoryItem(text, "模块", "功能", "查看", true)));
      rejectReport(1, List.of(new DirectoryItem("read", text, "功能", "查看", true)));
      rejectReport(1, List.of(new DirectoryItem("read", "模块", text, "查看", true)));
      rejectReport(1, List.of(new DirectoryItem("read", "模块", "功能", text, true)));
    }
    rejectReport(1, List.of(item("read", true), item("read", true)));
    assertThat(permission.active).isTrue();
  }

  @Test
  @DisplayName("ACC-F-013 同版本只接受等价快照，不允许新项、改名或隐式删除有效项")
  void repeatedDirectory() {
    assertThat(service.report(1, 1, List.of(item("read", true)))).isFalse();
    rejectReport(1, List.of());
    for (DirectoryItem changed :
        List.of(
            new DirectoryItem("read", "改模块", "功能", "查看", true),
            new DirectoryItem("read", "模块", "改功能", "查看", true),
            new DirectoryItem("read", "模块", "功能", "改动作", true),
            item("read", false))) {
      rejectReport(1, List.of(changed));
    }
    rejectReport(1, List.of(item("read", true), item("new", true)));
    permission.active = false;
    assertThat(service.report(1, 1, List.of())).isFalse();
  }

  @Test
  @DisplayName("ACC-F-013 明确停用撤下角色选择，重新启用和新增动作不自动授权")
  void directoryEvolution() {
    roleRows.add(role(2, 2L, 1L, false, "别的系统"));
    roleRows.add(role(3, 1L, 1L, false, "空角色"));
    assertThat(service.report(1, 2, List.of(item("read", false), item("new", true)))).isTrue();
    assertThat(role.permissions).isEmpty();
    assertThat(permission.active).isFalse();
    assertThat(service.report(1, 3, List.of())).isTrue();
    assertThat(service.report(1, 4, List.of(item("read", true)))).isTrue();
    assertThat(role.permissions).isEmpty();
  }

  private void reject(RoleChange change) {
    assertThatThrownBy(() -> service.save(1, change)).isInstanceOf(BusinessException.class);
  }

  private void rejectReport(long revision, List<DirectoryItem> items) {
    assertThatThrownBy(() -> service.report(1, revision, items))
        .isInstanceOf(BusinessException.class);
  }

  private static RoleChange change(Long id, String name, long version, Set<Long> chosen) {
    return new RoleChange(id, 1L, 1L, name, false, true, version, chosen);
  }

  private static DirectoryItem item(String key, boolean active) {
    return new DirectoryItem(key, "模块", "功能", "查看", active);
  }

  private static RoleEntity role(
      long id, Long system, Long department, boolean template, String name) {
    var role = new RoleEntity();
    ReflectionTestUtils.setField(role, "id", id);
    role.systemId = system;
    role.departmentId = department;
    role.template = template;
    role.name = name;
    return role;
  }

  private static IntegrationApi.SystemFact system(boolean active, long revision) {
    return new IntegrationApi.SystemFact(
        1L, "oa", "办公", active, "hash", "hash", null, null, revision, 0, Set.of(1L));
  }

  private static OrganizationApi.Employee actor(boolean global) {
    return new OrganizationApi.Employee(
        1L, "a", "管理员", "phone", 1L, 1L, "岗位", true, global, !global, 0);
  }
}
