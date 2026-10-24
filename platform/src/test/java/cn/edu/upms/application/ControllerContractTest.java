package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.authorization.api.BatchApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import cn.edu.upms.shared.FailureAdvice;
import cn.edu.upms.shared.PageResult;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 验证真实 HTTP 映射与 DTO，不替代 OidcIntegrationTest 中的安全过滤器和真实数据库. */
class ControllerContractTest {
  final IdentityApi identity = mock(IdentityApi.class);
  final OrganizationApi organization = mock(OrganizationApi.class);
  final IntegrationApi integration = mock(IntegrationApi.class);
  final CatalogApi catalog = mock(CatalogApi.class);
  final AuthorizationApi authorization = mock(AuthorizationApi.class);
  final BatchApi batches = mock(BatchApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final EmployeeLifecycle lifecycle = mock(EmployeeLifecycle.class);
  final Principal principal = () -> "session-identity";
  final AdminController controller =
      new AdminController(
          identity, organization, integration, catalog, authorization, batches, audit, lifecycle);
  final OrganizationApi.Employee employee =
      new OrganizationApi.Employee(1L, "admin", "管理员", "phone", 1L, 1L, "岗位", true, true, false, 0);
  final IntegrationApi.SystemFact system =
      new IntegrationApi.SystemFact(
          1L,
          "oa",
          "办公",
          true,
          "MUST_NOT_EXPOSE",
          "MUST_NOT_EXPOSE",
          "https://example.com/cb",
          "https://example.com",
          1,
          0,
          Set.of(1L));
  final CatalogApi.Role role = new CatalogApi.Role(1L, 1L, 1L, "角色", false, true, 0, Set.of(1L));
  MockMvc mvc;

  @BeforeEach
  void fixtures() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                controller,
                new RuntimeController(identity, authorization, audit),
                new DirectoryController(identity, catalog, audit))
            .setControllerAdvice(new FailureAdvice())
            .build();
    when(identity.browser(principal))
        .thenReturn(new IdentityApi.SessionIdentity(1, "session", Instant.now().plusSeconds(60)));
    when(organization.administrator(1)).thenReturn(employee);
    when(organization.employee(1)).thenReturn(employee);
    when(integration.systems(1)).thenReturn(List.of(system));
    when(integration.save(anyLong(), any())).thenReturn(system);
    when(integration.bind(anyLong(), anyLong(), any(), anyLong())).thenReturn(system);
    when(catalog.copy(1, 1, 1, "角色")).thenReturn(role);
    when(catalog.save(anyLong(), any())).thenReturn(role);
    when(audit.query(any(), any(), any(), any(), any(), any(), any(int.class), any(int.class)))
        .thenReturn(new PageResult<>(List.of(), 0, 0, 100));
  }

  @Test
  @DisplayName("后台查询 HTTP 契约不返回系统凭据，操作者只来自可信会话")
  void readContracts() throws Exception {
    for (String path :
        List.of(
            "me",
            "statistics",
            "companies",
            "departments",
            "employees",
            "employees/1",
            "roles",
            "roles/1/affected",
            "employees/1/permissions",
            "batches",
            "audit",
            "systems/1/permissions")) {
      mvc.perform(get("/api/admin/" + path).principal(principal)).andExpect(status().isOk());
    }
    String body =
        mvc.perform(get("/api/admin/systems").principal(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("oa"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("MUST_NOT_EXPOSE", "clientSecret", "directorySecret");
    verify(organization).requireEmployeeScope(1, 1);
    mvc.perform(get("/api/admin/systems/999/permissions").principal(principal))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("后台写入 HTTP DTO 完整映射，普通字段不能伪造操作者")
  void writeContracts() throws Exception {
    send("companies", "{\"name\":\"公司\",\"active\":true,\"version\":0}", 200);
    send("departments", "{\"id\":null,\"active\":false,\"companyId\":1,\"name\":\"部门\"}", 200);
    send("departments", "{\"companyId\":1,\"name\":\"部门\",\"active\":true}", 200);
    send("departments", "{\"id\":2,\"companyId\":1,\"name\":\"部门\",\"active\":false}", 200);
    send("departments", "{\"id\":1,\"companyId\":1,\"name\":\"部门\",\"active\":false}", 409);
    send(
        "employees",
        "{\"username\":\"u\",\"name\":\"员工\",\"phone\":\"p\",\"companyId\":1,"
            + "\"departmentId\":1,\"positionName\":\"岗位\"}",
        200);
    send("employees/1/state", "{\"active\":false,\"version\":0}", 200);
    send("employees/1/transfer", "{\"departmentId\":2,\"version\":0}", 200);
    send(
        "administrators",
        "{\"employeeId\":1,\"superAdmin\":false,\"departmentAdmin\":true,\"version\":0}",
        200);
    send("systems", "{\"key\":\"oa\",\"name\":\"办公\",\"active\":true}", 200);
    send("systems/1/departments", "{\"departments\":[1],\"version\":0}", 200);
    send("systems/1/credentials", "{}", 400);
    when(integration.rotateCredentials(1, 1, 0))
        .thenReturn(
            new IntegrationApi.Credentials("oa", "new", "oa-directory", "new-directory", 1));
    mvc.perform(
            post("/api/admin/systems/1/credentials")
                .principal(principal)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value("oa"));
    send(
        "roles",
        "{\"systemId\":1,\"departmentId\":1,\"name\":\"角色\",\"active\":true,\"permissions\":[1]}",
        200);
    send("roles/copy", "{\"templateId\":1,\"departmentId\":1,\"name\":\"角色\"}", 200);
    send("roles/1/delete", "{}", 400);
    send("roles/1/delete", "{\"version\":0}", 200);
    send(
        "assignments",
        "{\"employeeId\":1,\"systemId\":1,\"roleId\":1,\"expectedVersion\":-1}",
        200);
    send(
        "batches",
        "{\"requestKey\":\"request-key\",\"systemId\":1,\"roleId\":1,"
            + "\"targets\":[{\"employeeId\":1,\"expectedVersion\":-1}]}",
        200);
    send("batches/1/retry", "[{\"employeeId\":1,\"expectedVersion\":0}]", 200);
    verify(authorization).change(1, 1, 1, 1L, -1);
    verify(lifecycle).state(1, 1, false, 0);
    verify(lifecycle).transfer(1, 1, 2, 0);
    verify(organization).appoint(1, 1, false, true, 0);
  }

  @Test
  @DisplayName("日志范围和 CSV 转义：空引用、多页结果、引号与公式均安全输出")
  void auditAndRestrictedAdministrator() throws Exception {
    var first =
        new AuditApi.View(
            1L, Instant.EPOCH, null, null, null, null, "EVENT", "=formula", "SUCCESS", "含\"引号");
    var second =
        new AuditApi.View(2L, Instant.EPOCH, 1L, 1L, null, 1L, "EVENT", "plain", "SUCCESS", "说明");
    when(audit.query(null, null, null, null, null, null, 0, 100))
        .thenReturn(new PageResult<>(List.of(first), 101, 0, 100));
    when(audit.query(null, null, null, null, null, null, 1, 100))
        .thenReturn(new PageResult<>(List.of(second), 101, 1, 100));
    var response = new MockHttpServletResponse();
    controller.export(principal, null, null, null, null, null, null, response);
    assertThat(response.getContentAsString()).contains("'=formula", "含\"\"引号", "plain");
    mvc.perform(get("/api/admin/audit").principal(principal).param("departmentId", "1"))
        .andExpect(status().isOk());
    when(organization.administrator(1))
        .thenReturn(
            new OrganizationApi.Employee(
                1L, "dept", "管理员", "p", 1L, 1L, "岗位", true, false, true, 0));
    mvc.perform(get("/api/admin/statistics").principal(principal))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/audit").principal(principal)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("运行鉴权身份来自令牌，目录上报失败不伪装成功，输入验证采用 400")
  void runtimeAndDirectoryContracts() throws Exception {
    when(identity.token("token", "oa"))
        .thenReturn(new IdentityApi.TokenIdentity(1, 1, "s", Instant.now().plusSeconds(60)));
    when(authorization.decide(1, 1, "read"))
        .thenReturn(new AuthorizationApi.Decision(true, "ALLOW"));
    mvc.perform(
            post("/api/runtime/check")
                .header("Authorization", "Bearer token")
                .contentType("application/json")
                .content("{\"system\":\"oa\",\"permission\":\"read\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));
    mvc.perform(
            post("/api/runtime/check")
                .header("Authorization", "Bearer token")
                .contentType("application/json")
                .content("{\"system\":\"\",\"permission\":\"read\"}"))
        .andExpect(status().isBadRequest());
    for (String header : new String[] {null, "Basic x", "Bearer " + "x".repeat(4096)}) {
      assertThat(
              org.assertj.core.api.Assertions.catchThrowable(
                  () -> RuntimeController.bearer(header)))
          .isInstanceOf(BusinessException.class);
    }
    when(identity.directory("directory-token")).thenReturn(1L);
    when(catalog.report(1, 2, List.of())).thenReturn(true);
    mvc.perform(
            post("/api/directory/report")
                .header("Authorization", "Bearer directory-token")
                .contentType("application/json")
                .content("{\"revision\":2,\"items\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.changed").value(true));
    when(catalog.report(1, 2, List.of())).thenThrow(BusinessException.conflict("重复版本内容不同"));
    mvc.perform(
            post("/api/directory/report")
                .header("Authorization", "Bearer directory-token")
                .contentType("application/json")
                .content("{\"revision\":2,\"items\":[]}"))
        .andExpect(status().isConflict());
    var request = new MockHttpServletRequest();
    request.setAttribute(
        org.springframework.security.web.csrf.CsrfToken.class.getName(),
        new DefaultCsrfToken("X-CSRF", "_csrf", "value"));
    assertThat(new LoginController().csrf(request)).containsEntry("token", "value");
    var loginModel = new org.springframework.ui.ExtendedModelMap();
    assertThat(new LoginController().login(loginModel)).isEqualTo("login");
    assertThat(loginModel).containsEntry("oaUrl", "http://localhost:18081");
    assertThat(new FrontendController().index()).isEqualTo("forward:/index.html");
  }

  private void send(String path, String content, int expected) throws Exception {
    mvc.perform(
            post("/api/admin/" + path)
                .principal(principal)
                .contentType("application/json")
                .content(content))
        .andExpect(status().is(expected));
  }
}
