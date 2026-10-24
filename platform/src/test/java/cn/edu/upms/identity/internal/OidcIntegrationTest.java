package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import cn.edu.upms.application.EmployeeLifecycle;
import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.authorization.api.BatchApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.organization.api.OrganizationImportApi;
import cn.edu.upms.shared.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** 使用真实框架过滤器、真实 MySQL 与签名器验证协议，不自行模拟授权码成功结果. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/upms_test?serverTimezone=Asia/Shanghai",
      "upms.seed=baseline",
      "upms.cache-enabled=true",
      "upms.source-directory=../docs"
    })
@AutoConfigureMockMvc
class OidcIntegrationTest {
  @Test
  @DisplayName("ACC-F-003 UserInfo 校验访问令牌与统一会话，目录令牌不具备 openid 范围")
  void userInfoAndUnauthenticatedAdmin() throws Exception {
    var session = login(20);
    var tokens = tokens(code(session, "userinfo-state", "nonce"), VERIFIER, "oa", "oa-demo-secret");
    String access = tokens.get("access_token").asText();
    var info =
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access))
            .andReturn()
            .getResponse();
    assertThat(info.getStatus()).isEqualTo(200);
    assertThat(json.readTree(info.getContentAsString()).get("sub").asText()).isEqualTo("20");
    assertThat(
            mvc.perform(get("/userinfo").header("Authorization", "Bearer unknown"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(mvc.perform(get("/api/admin/me")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    var before = store.findByToken(access, OAuth2TokenType.ACCESS_TOKEN);
    store.save(
        OAuth2Authorization.from(before)
            .accessToken(
                new org.springframework.security.oauth2.core.OAuth2AccessToken(
                    org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
                    access,
                    Instant.now().minusSeconds(120),
                    Instant.now().minusSeconds(60),
                    Set.of("openid")))
            .build());
    assertThat(
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    store.save(before);
    mvc.perform(post("/logout").session(session).with(csrf()));
    assertThat(
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("ACC-F-006 标准 RP 发起退出验证回调和 state，并使当前会话令牌失效")
  void standardRpLogout() throws Exception {
    var session = login(20);
    var tokens = tokens(code(session, "rp-state", "nonce"), VERIFIER, "oa", "oa-demo-secret");
    var result =
        mvc.perform(
                post("/connect/logout")
                    .session(session)
                    .with(csrf())
                    .param("id_token_hint", tokens.get("id_token").asText())
                    .param("post_logout_redirect_uri", "http://localhost:18081")
                    .param("state", "logout-state"))
            .andReturn()
            .getResponse();
    assertThat(result.getStatus()).isEqualTo(302);
    assertThat(result.getRedirectedUrl()).isEqualTo("http://localhost:18081?state=logout-state");
    assertThat(runtime(tokens.get("access_token").asText(), "oa")).isEqualTo(401);
  }

  private static final String REDIRECT = "http://localhost:18081/login/oauth2/code/upms";
  private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789-ABCDEFG";
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private OrganizationApi organization;
  @Autowired private OrganizationImportApi importer;
  @Autowired private IntegrationApi integration;
  @Autowired private OAuth2AuthorizationService store;
  @Autowired private JwtDecoder decoder;
  @Autowired private IdentityService identity;
  @Autowired private EmployeeLifecycle lifecycle;
  @Autowired private AuditApi audit;
  @Autowired private TransactionTemplate transaction;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AuthorizationApi authorization;
  @Autowired private BatchApi batches;
  @Autowired private CatalogApi catalog;

  @BeforeEach
  void prepare() {
    transaction.executeWithoutResult(
        status -> importer.demonstrationAdministrators(List.of("SG000001"), List.of("SG000011")));
    var system = integration.byKey("oa");
    integration.bind(1, system.id(), Set.of(1L), system.version());
  }

  @Test
  @DisplayName("ACC-F-003、004 OIDC 返回 state、签名身份与 nonce，访问令牌不透明且无刷新令牌")
  void standards() throws Exception {
    var session = login(20);
    String code = code(session, "state-test", "nonce-test");
    var authorization = store.findByToken(code, new OAuth2TokenType("code"));
    assertThat(authorization.getToken(OAuth2AuthorizationCode.class).getToken().getExpiresAt())
        .isEqualTo(
            authorization
                .getToken(OAuth2AuthorizationCode.class)
                .getToken()
                .getIssuedAt()
                .plusSeconds(60));
    var tokens = tokens(code, VERIFIER, "oa", "oa-demo-secret");
    assertThat(tokens.has("refresh_token")).isFalse();
    assertThat(tokens.get("access_token").asText()).doesNotContain(".");
    var idToken = decoder.decode(tokens.get("id_token").asText());
    assertThat(idToken.getIssuer().toString()).isEqualTo("http://localhost:18080");
    assertThat(idToken.getAudience()).containsExactly("oa");
    assertThat(idToken.getClaimAsString("nonce")).isEqualTo("nonce-test");
    assertThat(idToken.getClaims()).doesNotContainKeys("permissions", "roles");
    assertThat(identity.token(tokens.get("access_token").asText(), "oa").employeeId())
        .isEqualTo(20);
    assertThat(
            mvc.perform(get("/.well-known/openid-configuration"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  @Test
  @DisplayName("ACC-F-004 授权码并发兑换仅一次成功，重复兑换不能重新发行令牌")
  void atomicExchange() throws Exception {
    String code = code(login(20), "concurrent", "nonce");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> exchange(code, VERIFIER, "oa", "oa-demo-secret"));
      var second = pool.submit(() -> exchange(code, VERIFIER, "oa", "oa-demo-secret"));
      assertThat(
              List.of(
                  first.get().getResponse().getStatus(), second.get().getResponse().getStatus()))
          .containsExactlyInAnyOrder(200, 400);
    }
    assertThat(exchange(code, VERIFIER, "oa", "oa-demo-secret").getResponse().getStatus())
        .isEqualTo(400);
  }

  @Test
  @DisplayName("ACC-F-004 错误 PKCE、过期码、跨客户端兑换与未登记回调均拒绝")
  void rejectedExchange() throws Exception {
    var session = login(20);
    assertThat(
            exchange(code(session, "pkce", "n"), VERIFIER + "x", "oa", "oa-demo-secret")
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    assertThat(
            exchange(code(session, "client", "n"), VERIFIER, "knowledge", "knowledge-demo-secret")
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    String expired = code(session, "expired", "n");
    var old = store.findByToken(expired, new OAuth2TokenType("code"));
    store.save(
        OAuth2Authorization.from(old)
            .token(
                new OAuth2AuthorizationCode(
                    expired, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)))
            .build());
    assertThat(exchange(expired, VERIFIER, "oa", "oa-demo-secret").getResponse().getStatus())
        .isEqualTo(400);
    var response =
        mvc.perform(
                get("/oauth2/authorize")
                    .session(session)
                    .queryParam("client_id", "oa")
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid")
                    .queryParam("redirect_uri", "http://localhost:19999/invalid")
                    .queryParam("state", "s")
                    .queryParam("code_challenge", challenge())
                    .queryParam("code_challenge_method", "S256"))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("ACC-F-006、014 同账号浏览器会话独立；停用撤销全部会话，重新启用不恢复会话")
  void independentSessionsAndDisable() throws Exception {
    var a = login(20);
    var b = login(20);
    var ta =
        tokens(code(a, "a", "n"), VERIFIER, "oa", "oa-demo-secret").get("access_token").asText();
    var tb =
        tokens(code(b, "b", "n"), VERIFIER, "oa", "oa-demo-secret").get("access_token").asText();
    assertThat(
            mvc.perform(post("/logout").session(a).with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(302);
    assertThat(runtime(ta, "oa")).isEqualTo(401);
    var introspection =
        mvc.perform(
                post("/oauth2/introspect")
                    .with(httpBasic("oa", "oa-demo-secret"))
                    .param("token", ta))
            .andReturn();
    assertThat(
            json.readTree(introspection.getResponse().getContentAsString())
                .get("active")
                .asBoolean())
        .isFalse();
    assertThat(identity.token(tb, "oa").employeeId()).isEqualTo(20);
    try {
      lifecycle.state(1, 20, false, organization.employee(20).version());
      assertThat(runtime(tb, "oa")).isEqualTo(401);
      var employee = organization.employee(20);
      assertThat(
              mvc.perform(
                      post("/login")
                          .with(csrf())
                          .param("username", employee.username())
                          .param("phone", employee.phone()))
                  .andReturn()
                  .getResponse()
                  .getRedirectedUrl())
          .isEqualTo("/login?error");
    } finally {
      lifecycle.state(1, 20, true, organization.employee(20).version());
    }
    assertThat(runtime(tb, "oa")).isEqualTo(401);
  }

  @Test
  @DisplayName("ACC-F-012、018 系统令牌不能冒用其他系统，目录身份不能执行员工鉴权")
  void separateCredentials() throws Exception {
    var token =
        tokens(code(login(20), "x", "n"), VERIFIER, "oa", "oa-demo-secret")
            .get("access_token")
            .asText();
    assertThat(runtime(token, "knowledge")).isEqualTo(403);
    var result =
        mvc.perform(
                post("/oauth2/token")
                    .with(httpBasic("oa-directory", "oa-directory-secret"))
                    .param("grant_type", "client_credentials")
                    .param("scope", "directory.report"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    var directoryToken =
        json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
    assertThat(identity.directory(directoryToken)).isEqualTo(integration.byKey("oa").id());
    assertThat(runtime(directoryToken, "oa")).isEqualTo(401);
  }

  @Test
  @DisplayName("ACC-F-007 后台写请求缺少 CSRF 被拒绝，部门日志导出不能越范围")
  void csrfAndExportScope() throws Exception {
    var session = login(11);
    assertThat(
            mvc.perform(
                    post("/api/admin/assignments")
                        .session(session)
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            mvc.perform(get("/api/admin/audit/export").session(session).param("departmentId", "2"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            mvc.perform(get("/api/admin/audit/export").session(session))
                .andReturn()
                .getResponse()
                .getContentType())
        .startsWith("text/csv");
  }

  @Test
  @DisplayName("ACC-F-011 整批越范围拒绝，部分版本冲突保留成功项，重复提交及明确重试可追溯")
  void batchResults() {
    long systemId = integration.byKey("oa").id();
    var role = testRole(systemId);
    var v23 = authorization.assignment(23, systemId).version();
    var v24 = authorization.assignment(24, systemId).version();
    var outOfScope =
        new BatchApi.Request(
            UUID.randomUUID().toString(),
            systemId,
            role.id(),
            List.of(new BatchApi.Target(23, v23), new BatchApi.Target(29, -1)));
    assertThatThrownBy(() -> batches.submit(11, outOfScope)).isInstanceOf(BusinessException.class);
    assertThat(authorization.assignment(23, systemId).version()).isEqualTo(v23);
    var request =
        new BatchApi.Request(
            UUID.randomUUID().toString(),
            systemId,
            role.id(),
            List.of(new BatchApi.Target(23, v23), new BatchApi.Target(24, v24 - 1)));
    var job = batches.submit(11, request);
    assertThat(job.status()).isEqualTo("PARTIAL_FAILURE");
    assertThat(job.items().stream().map(BatchApi.Item::status))
        .containsExactly("SUCCESS", "FAILED");
    assertThat(batches.submit(11, request).id()).isEqualTo(job.id());
    long successfulVersion = authorization.assignment(23, systemId).version();
    assertThat(batches.retry(11, job.id(), List.of(new BatchApi.Target(24, v24))).status())
        .isEqualTo("COMPLETED");
    assertThat(authorization.assignment(23, systemId).version()).isEqualTo(successfulVersion);
    for (long employeeId : List.of(23L, 24L)) {
      authorization.change(
          11, employeeId, systemId, null, authorization.assignment(employeeId, systemId).version());
    }
  }

  @Test
  @DisplayName("ACC-F-009 删除角色先拒绝仍有分配，撤权后逻辑删除并保留审计")
  void roleDeletion() throws Exception {
    var session = login(1);
    long system = integration.byKey("oa").id();
    var role =
        catalog.copy(
            1,
            catalog.roles(1, system, null).stream()
                .filter(CatalogApi.Role::template)
                .findFirst()
                .orElseThrow()
                .id(),
            1,
            "删除测试-" + UUID.randomUUID());
    authorization.change(1, 26, system, role.id(), authorization.assignment(26, system).version());
    String payload = "{\"version\":" + role.version() + "}";
    assertThat(
            mvc.perform(
                    post("/api/admin/roles/" + role.id() + "/delete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    authorization.change(1, 26, system, null, authorization.assignment(26, system).version());
    assertThat(
            mvc.perform(
                    post("/api/admin/roles/" + role.id() + "/delete")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThatThrownBy(() -> catalog.role(role.id())).isInstanceOf(BusinessException.class);
    assertThat(
            audit
                .query(null, "ROLE_DELETED", null, "role:" + role.id(), null, null, 0, 100)
                .items())
        .isNotEmpty();
  }

  @Test
  @DisplayName("ACC-P-005 缓存命中不续期，角色撤去权限后两秒期限内影响后续鉴权")
  void cachedFactsExpire() throws Exception {
    long systemId = integration.byKey("oa").id();
    var role =
        catalog.copy(
            1,
            catalog.roles(1, systemId, null).stream()
                .filter(CatalogApi.Role::template)
                .findFirst()
                .orElseThrow()
                .id(),
            1,
            "缓存测试-" + UUID.randomUUID());
    authorization.change(
        1, 27, systemId, role.id(), authorization.assignment(27, systemId).version());
    String key =
        catalog.permissions(systemId).stream()
            .filter(p -> p.active() && role.permissions().contains(p.id()))
            .findFirst()
            .orElseThrow()
            .key();
    assertThat(authorization.decide(27, systemId, key).allowed()).isTrue();
    catalog.save(
        1,
        new CatalogApi.RoleChange(
            role.id(), systemId, 1L, role.name(), false, true, role.version(), Set.of()));
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
    while (System.nanoTime() < deadline) {
      authorization.decide(27, systemId, key);
      Thread.sleep(50);
    }
    assertThat(authorization.decide(27, systemId, key).allowed()).isFalse();
    authorization.change(1, 27, systemId, null, authorization.assignment(27, systemId).version());
  }

  @Test
  @DisplayName("ACC-F-015 调动必须手工撤权并等待生效，取消旧部门管理员且不继承角色")
  void manualTransfer() throws Exception {
    long systemId = integration.byKey("oa").id();
    var role = testRole(systemId);
    authorization.change(
        1, 25, systemId, role.id(), authorization.assignment(25, systemId).version());
    assertThatThrownBy(() -> lifecycle.transfer(1, 25, 2, organization.employee(25).version()))
        .isInstanceOf(BusinessException.class);
    var employee = organization.employee(25);
    organization.appoint(1, 25, false, true, employee.version());
    authorization.change(1, 25, systemId, null, authorization.assignment(25, systemId).version());
    assertThatThrownBy(() -> lifecycle.transfer(1, 25, 2, organization.employee(25).version()))
        .isInstanceOf(BusinessException.class);
    Thread.sleep(3100);
    try {
      var moved = lifecycle.transfer(1, 25, 2, organization.employee(25).version());
      assertThat(moved.departmentAdmin()).isFalse();
      assertThat(authorization.effective(25, systemId).permissions()).isEmpty();
      assertThatThrownBy(
              () ->
                  authorization.change(
                      11,
                      25,
                      systemId,
                      role.id(),
                      authorization.assignment(25, systemId).version()))
          .isInstanceOf(BusinessException.class);
    } finally {
      lifecycle.transfer(1, 25, 1, organization.employee(25).version());
    }
  }

  @Test
  @DisplayName("ACC-F-017 真实审计表故障回滚变更、阻止登录鉴权，退出不恢复且报告缺口")
  void auditStorageFailure() throws Exception {
    var session = login(22);
    var token =
        tokens(code(session, "fault", "n"), VERIFIER, "oa", "oa-demo-secret")
            .get("access_token")
            .asText();
    var company = organization.companies(1).getFirst();
    boolean renamed = false;
    try {
      // 只改变专用测试库的审计表，finally 恢复；不触碰演示库或来源资料。
      jdbc.execute("RENAME TABLE audit_event TO fault_audit_event");
      renamed = true;
      assertThatThrownBy(
              () -> organization.saveCompany(1, company.id(), "不应提交的故障改名", true, company.version()))
          .isInstanceOf(BusinessException.class);
      assertThat(organization.companies(1).getFirst().name()).isEqualTo(company.name());
      assertThat(runtime(token, "oa")).isEqualTo(503);
      var employee = organization.employee(22);
      assertThat(
              mvc.perform(
                      post("/login")
                          .with(csrf())
                          .param("username", employee.username())
                          .param("phone", employee.phone()))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .isEqualTo(503);
      assertThat(
              mvc.perform(post("/logout").session(session).with(csrf()))
                  .andReturn()
                  .getResponse()
                  .getRedirectedUrl())
          .isEqualTo("/login?audit=failed");
    } finally {
      if (renamed) {
        jdbc.execute("RENAME TABLE fault_audit_event TO audit_event");
      }
      for (int attempt = 0;
          attempt < 30 && Boolean.TRUE.equals(audit.statistics().get("paused"));
          attempt++) {
        Thread.sleep(200);
      }
    }
    assertThat(audit.statistics().get("paused")).isEqualTo(false);
    assertThat(audit.query(null, "AUDIT_RECOVERED", null, null, null, null, 0, 100).items())
        .isNotEmpty();
    assertThat(runtime(token, "oa")).isEqualTo(401);
  }

  private CatalogApi.Role testRole(long systemId) {
    return catalog.roles(1, systemId, 1L).stream()
        .filter(r -> !r.template() && r.name().equals("协议集成测试角色"))
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
                    "协议集成测试角色"));
  }

  private MockHttpSession login(long id) throws Exception {
    var employee = organization.employee(id);
    var result =
        mvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", employee.username())
                    .param("phone", employee.phone()))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(302);
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private String code(MockHttpSession session, String state, String nonce) throws Exception {
    var response =
        mvc.perform(
                get("/oauth2/authorize")
                    .session(session)
                    .queryParam("client_id", "oa")
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid profile")
                    .queryParam("redirect_uri", REDIRECT)
                    .queryParam("state", state)
                    .queryParam("nonce", nonce)
                    .queryParam("code_challenge", challenge())
                    .queryParam("code_challenge_method", "S256"))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(302);
    var query =
        UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build().getQueryParams();
    assertThat(query.getFirst("state")).isEqualTo(state);
    assertThat(query.getFirst("code")).isNotBlank();
    return query.getFirst("code");
  }

  private JsonNode tokens(String code, String verifier, String client, String secret)
      throws Exception {
    var result = exchange(code, verifier, client, secret);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(result.getResponse().getContentAsString());
  }

  private MvcResult exchange(String code, String verifier, String client, String secret)
      throws Exception {
    return mvc.perform(
            post("/oauth2/token")
                .with(httpBasic(client, secret))
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", REDIRECT)
                .param("code_verifier", verifier))
        .andReturn();
  }

  private int runtime(String token, String system) throws Exception {
    return mvc.perform(
            post("/api/runtime/check")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"system\":\"" + system + "\",\"permission\":\"f001:查看\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private static String challenge() throws Exception {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
  }
}
