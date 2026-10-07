package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import cn.edu.upms.shared.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 在项目专用真实 MySQL 上验证导入、范围隔离、角色与目录事务. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/upms_test?serverTimezone=Asia/Shanghai",
      "upms.seed=baseline",
      "upms.source-directory=../docs"
    })
@Transactional
class FoundationIntegrationTest {
  @Autowired private OrganizationApi organization;
  @Autowired private OrganizationImportApi importer;
  @Autowired private IntegrationApi integration;
  @Autowired private CatalogApi catalog;
  @Autowired private AuditApi audit;

  @BeforeEach
  void demonstrationScope() {
    importer.demonstrationAdministrators(List.of("SG000001"), List.of("SG000011"));
  }

  @Test
  @DisplayName("ACC-F-001 基础数据无岗位推断授权，组织与系统数量准确")
  void baselineCounts() {
    assertThat(importer.counts())
        .containsEntry("employees", 10000L)
        .containsEntry("companies", 10L)
        .containsEntry("departments", 101L)
        .containsEntry("unconfirmedDepartments", 54L);
    assertThat(integration.registered()).hasSize(17);
    assertThat(catalog.roles(1, null, null)).hasSize(85).allMatch(CatalogApi.Role::template);
    assertThat(organization.employee(12).departmentAdmin()).isFalse();
  }

  @Test
  @DisplayName("ACC-F-002 用户名和手机号同时匹配，普通员工无后台资格")
  void loginAndAdministrator() {
    var employee = organization.employee(13);
    assertThat(organization.authenticate(employee.username(), employee.phone()).id())
        .isEqualTo(13L);
    assertThatThrownBy(() -> organization.authenticate(employee.username(), "00000000000"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> organization.administrator(13)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-007 越部门查询及模板修改被拒绝")
  void administratorScope() {
    assertThat(organization.employees(11, null, null, 0, 100).items())
        .allMatch(e -> e.departmentId() == 1);
    assertThatThrownBy(() -> organization.employees(11, null, 2L, 0, 10))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> organization.requireDepartmentScope(11, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-008 多对多可用系统以及版本冲突检测")
  void systemScope() {
    var oa = integration.byKey("oa");
    var changed = integration.bind(1, oa.id(), Set.of(1L, 2L), oa.version());
    assertThat(integration.available(oa.id(), 1L)).isTrue();
    assertThat(integration.available(oa.id(), 2L)).isTrue();
    assertThatThrownBy(() -> integration.bind(1, oa.id(), Set.of(), oa.version()))
        .isInstanceOf(BusinessException.class);
    assertThat(changed.version()).isGreaterThan(oa.version());
  }

  @Test
  @DisplayName("ACC-F-009 模板复制隔离与新增权限不自动授予")
  void isolatedCopy() {
    var oa = integration.byKey("oa");
    integration.bind(1, oa.id(), Set.of(1L), oa.version());
    var template = catalog.roles(11, oa.id(), null).getFirst();
    var copy = catalog.copy(11, template.id(), 1, "测试角色");
    var updated =
        catalog.save(
            1,
            new CatalogApi.RoleChange(
                template.id(),
                oa.id(),
                null,
                template.name(),
                true,
                true,
                template.version(),
                Set.of()));
    assertThat(updated.permissions()).isEmpty();
    assertThat(catalog.role(copy.id()).permissions()).isEqualTo(template.permissions());
  }

  @Test
  @DisplayName("ACC-F-013 无效目录不改变旧版本，停用与重报幂等")
  void directoryValidation() {
    var oa = integration.byKey("oa");
    var old = catalog.permissions(oa.id());
    assertThatThrownBy(
            () ->
                catalog.report(
                    oa.id(),
                    2,
                    List.of(
                        new CatalogApi.DirectoryItem("duplicate", "m", "f", "查看", true),
                        new CatalogApi.DirectoryItem("duplicate", "m", "f", "查看", true))))
        .isInstanceOf(BusinessException.class);
    assertThat(integration.system(oa.id()).directoryRevision()).isEqualTo(1);
    var items =
        old.stream()
            .map(
                p ->
                    new CatalogApi.DirectoryItem(
                        p.key(), p.module(), p.feature(), p.action(), false))
            .toList();
    assertThat(catalog.report(oa.id(), 2, items)).isTrue();
    assertThat(catalog.report(oa.id(), 2, items)).isFalse();
    assertThat(catalog.permissions(oa.id())).noneMatch(CatalogApi.Permission::active);
  }

  @Test
  @DisplayName("ACC-F-018 唯一身份与最后一名最高管理员受到保护")
  void invariants() {
    var highest = organization.employee(1);
    assertThatThrownBy(() -> organization.appoint(1, 1, false, false, highest.version()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                organization.saveEmployee(
                    1,
                    new OrganizationApi.EmployeeChange(
                        null, highest.username(), "重复账号", "13900009999", 1L, 1L, "测试", 0)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(audit.statistics()).containsKey("paused");
  }
}
