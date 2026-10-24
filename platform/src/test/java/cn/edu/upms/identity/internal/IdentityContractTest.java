package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** 身份适配器的错误边界；协议签名、兑换竞争和数据库故障由真实集成测试验证. */
class IdentityContractTest {
  final Instant now = Instant.now();
  final UnifiedSessions sessions = new UnifiedSessions(Clock.fixed(now, ZoneOffset.UTC));
  final OrganizationApi organization = mock(OrganizationApi.class);
  final IntegrationApi integration = mock(IntegrationApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final OAuth2AuthorizationService store = mock(OAuth2AuthorizationService.class);
  final IdentityService identity =
      new IdentityService(sessions, store, organization, integration, audit);

  @Test
  @DisplayName("ACC-F-003 无授权、无访问令牌、过期和索引不一致均拒绝")
  void invalidTokens() {
    rejected(() -> identity.token("unknown", "oa"));
    final var principal = sessions.open(1);
    when(organization.employee(1)).thenReturn(employee(true));
    var empty = authorization(AuthorizationGrantType.AUTHORIZATION_CODE, "1").build();
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(empty);
    rejected(() -> identity.token("token", "oa"));
    var expired =
        authorization(AuthorizationGrantType.AUTHORIZATION_CODE, "1")
            .accessToken(
                new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "token",
                    now.minusSeconds(120),
                    now.minusSeconds(60)))
            .build();
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(expired);
    rejected(() -> identity.token("token", "oa"));
    var mismatch = token(AuthorizationGrantType.AUTHORIZATION_CODE, "1", principal, "different");
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(mismatch);
    rejected(() -> identity.token("token", "oa"));
    var valid = token(AuthorizationGrantType.AUTHORIZATION_CODE, "1", principal, "token");
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(valid);
    when(integration.system(1)).thenReturn(system(false));
    rejected(() -> identity.token("token", "oa"));
    when(integration.system(1)).thenReturn(system(true));
    rejected(() -> identity.token("token", "knowledge"));
    assertThat(identity.token("token", "oa").employeeId()).isEqualTo(1);
    when(organization.employee(1)).thenReturn(employee(false));
    rejected(() -> identity.token("token", "oa"));
    rejected(() -> identity.browser(null));
    assertThat(IdentityService.user("not-principal")).isNull();
  }

  @Test
  @DisplayName("目录访问要求客户端凭据授权、独立范围、目录身份和有效系统")
  void directoryIdentity() {
    var principal = sessions.open(1);
    for (var type :
        List.of(
            AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.CLIENT_CREDENTIALS)) {
      when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN))
          .thenReturn(token(type, "1", principal, "token"));
      rejected(() -> identity.directory("token"));
    }
    var directory =
        authorization(AuthorizationGrantType.CLIENT_CREDENTIALS, "1")
            .accessToken(
                new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "token",
                    now,
                    now.plusSeconds(60),
                    Set.of("directory.report")))
            .build();
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(directory);
    rejected(() -> identity.directory("token"));
    directory =
        authorization(AuthorizationGrantType.CLIENT_CREDENTIALS, "directory-1")
            .accessToken(directory.getAccessToken().getToken())
            .build();
    when(store.findByToken("token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(directory);
    when(integration.system(1)).thenReturn(system(false));
    rejected(() -> identity.directory("token"));
    when(integration.system(1)).thenReturn(system(true));
    assertThat(identity.directory("token")).isEqualTo(1);
    assertThat(identity.logout(null)).isTrue();
    when(organization.employee(1)).thenReturn(employee(true));
    assertThat(identity.logout(principal)).isTrue();
    assertThat(sessions.valid(principal)).isFalse();
    var next = sessions.open(1);
    identity.revokeEmployeeSessions(1);
    assertThat(sessions.valid(next)).isFalse();
  }

  @Test
  @DisplayName("授权存储重新保存后拒绝陈旧索引，移除时清理码、令牌和状态索引")
  void authorizationIndexes() {
    var actual = new AuthorizationStore();
    assertThat(actual.findByToken(null, null)).isNull();
    assertThat(actual.findByToken("missing", null)).isNull();
    var authorization =
        token(AuthorizationGrantType.AUTHORIZATION_CODE, "1", sessions.open(1), "old");
    actual.save(authorization);
    assertThat(actual.findById(authorization.getId())).isEqualTo(authorization);
    assertThat(actual.findByToken("old", null)).isEqualTo(authorization);
    var changed =
        OAuth2Authorization.from(authorization)
            .accessToken(
                new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER, "new", now, now.plusSeconds(60)))
            .token(new OAuth2AuthorizationCode("code", now, now.plusSeconds(60)))
            .token(new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of("sub", "1")))
            .attribute("state", "state")
            .build();
    actual.save(changed);
    assertThat(actual.findByToken("old", null)).isNull();
    for (String value : List.of("new", "code", "id-token", "state")) {
      assertThat(actual.findByToken(value, null)).isEqualTo(changed);
    }
    actual.remove(changed);
    assertThat(actual.findById(changed.getId())).isNull();
    assertThat(actual.findByToken("state", null)).isNull();
    assertThat(actual.findByToken("old", null)).isNull();
  }

  @Test
  @DisplayName("绝对会话过滤器移除过期上下文，未创建容器会话时也正常拒绝")
  void absoluteSessionFilter() throws Exception {
    var user = sessions.open(1);
    var service = mock(IdentityService.class);
    var filter = new AbsoluteSessionFilter(service);
    for (boolean withSession : List.of(false, true)) {
      var request = new MockHttpServletRequest();
      if (withSession) {
        request.setSession(new MockHttpSession());
      }
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
      doThrow(new BusinessException(401, "EXPIRED", "失效")).when(service).requireUser(user);
      try {
        filter.doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
      } finally {
        SecurityContextHolder.clearContext();
      }
    }
    var request = new MockHttpServletRequest();
    filter.doFilter(
        request, new MockHttpServletResponse(), (req, res) -> assertThat(req).isSameAs(request));
  }

  @Test
  @DisplayName("登录审计失败必须撤销刚创建的会话，UserInfo 仅提供身份资料")
  void loginFailureAndUserInfo() {
    when(organization.authenticate("admin", "phone")).thenReturn(employee(true));
    var provider = new DemoAuthenticationProvider(organization, audit, sessions);
    assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    assertThat(provider.supports(String.class)).isFalse();
    doThrow(BusinessException.unavailable()).when(audit).record(any());
    assertThatThrownBy(
            () ->
                provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", "phone")))
        .isInstanceOf(AuthenticationServiceException.class);
    when(organization.employee(1)).thenReturn(employee(true));
    var info =
        new UserInfoController(organization)
            .userInfo(new DefaultOAuth2AuthenticatedPrincipal("1", Map.of("sub", "1"), List.of()));
    assertThat(info).containsEntry("sub", "1").doesNotContainKeys("roles", "permissions", "phone");
  }

  private OAuth2Authorization token(
      AuthorizationGrantType type, String client, Principal user, String value) {
    return authorization(type, client)
        .attribute(Principal.class.getName(), user)
        .accessToken(
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, value, now, now.plusSeconds(60)))
        .build();
  }

  private OAuth2Authorization.Builder authorization(AuthorizationGrantType type, String id) {
    var client =
        RegisteredClient.withId(id)
            .clientId("oa")
            .authorizationGrantType(type)
            .redirectUri("http://localhost/cb")
            .build();
    return OAuth2Authorization.withRegisteredClient(client)
        .principalName("1")
        .authorizationGrantType(type);
  }

  private static IntegrationApi.SystemFact system(boolean active) {
    return new IntegrationApi.SystemFact(
        1L, "oa", "办公", active, "hash", "hash", null, null, 1, 0, Set.of(1L));
  }

  private static OrganizationApi.Employee employee(boolean active) {
    return new OrganizationApi.Employee(
        1L, "admin", "管理员", "phone", 1L, 1L, "岗位", active, true, false, 0);
  }

  private static void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOf(BusinessException.class);
  }
}
