package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.shared.BusinessException;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

/** 验证协议适配的故障转换和会话副作用；端点标准流程另由真实框架测试覆盖. */
class ProtocolAdaptersTest {
  final OAuth2AuthorizationService store = mock(OAuth2AuthorizationService.class);
  final IdentityService identity = mock(IdentityService.class);
  final AuditApi audit = mock(AuditApi.class);
  final Instant now = Instant.now();
  final RegisteredClient client =
      RegisteredClient.withId("client")
          .clientId("oa")
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("http://localhost/cb")
          .build();

  @Test
  @DisplayName("UserInfo 拒绝不存在、缺失、过期访问令牌和失效会话，目录身份仍受 scope 过滤")
  void userInfoBoundary() {
    var introspector = new UserInfoIntrospector(store, identity, audit);
    assertThatThrownBy(() -> introspector.introspect("missing"))
        .isInstanceOf(BadOpaqueTokenException.class);
    var empty =
        OAuth2Authorization.withRegisteredClient(client)
            .principalName("1")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    when(store.findByToken("empty", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(empty);
    assertThatThrownBy(() -> introspector.introspect("empty"))
        .isInstanceOf(BadOpaqueTokenException.class);
    var access =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access",
            now,
            now.plusSeconds(60),
            Set.of("openid"));
    var authorization =
        OAuth2Authorization.from(empty)
            .token(
                access,
                metadata ->
                    metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Map.of("sub", "1")))
            .build();
    when(store.findByToken("access", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization);
    assertThat(introspector.introspect("access").getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactly("SCOPE_openid");
    var user = new UnifiedPrincipal(1, "session", now.plusSeconds(60));
    var personal =
        OAuth2Authorization.from(authorization)
            .attribute(
                Principal.class.getName(),
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()))
            .build();
    when(store.findByToken("access", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(personal);
    assertThat(introspector.introspect("access").getName()).isEqualTo("1");
    doThrow(BusinessException.forbidden()).when(identity).requireUser(user);
    assertThatThrownBy(() -> introspector.introspect("access"))
        .isInstanceOf(BadOpaqueTokenException.class);
    when(store.findByToken("access", OAuth2TokenType.ACCESS_TOKEN))
        .thenReturn(
            OAuth2Authorization.from(authorization)
                .accessToken(
                    new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access",
                        now.minusSeconds(120),
                        now.minusSeconds(60)))
                .build());
    assertThatThrownBy(() -> introspector.introspect("access"))
        .isInstanceOf(BadOpaqueTokenException.class);
  }

  @Test
  @DisplayName("RP 退出即使没有当前浏览器或历史授权也安全结束，审计失败保留可见标记")
  void logoutBoundary() throws Exception {
    var handler = new UnifiedOidcLogoutHandler(store, identity);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var anonymous = UsernamePasswordAuthenticationToken.unauthenticated("anonymous", "");
    handler.logout(
        request,
        response,
        new OidcLogoutAuthenticationToken(
            "id", anonymous, null, "oa", "https://example.test/", "s"));
    verify(identity).logout(isNull());
    assertThat(request.getAttribute("auditGap")).isEqualTo(true);
    new AuditGapResponse(response, request).sendRedirect("https://example.test/?state=s");
    assertThat(response.getRedirectedUrl()).isEqualTo("https://example.test/?state=s&audit=failed");
    var user = new UnifiedPrincipal(1, "session", now.plusSeconds(60));
    var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    var authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .principalName("1")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute(Principal.class.getName(), auth)
            .build();
    when(store.findByToken(any(), any())).thenReturn(authorization);
    when(identity.logout(auth)).thenReturn(true);
    var signedIn = new MockHttpServletRequest();
    var session = new MockHttpSession();
    signedIn.setSession(session);
    handler.logout(
        signedIn,
        new MockHttpServletResponse(),
        new OidcLogoutAuthenticationToken(
            new OidcIdToken("id", now, now.plusSeconds(60), Map.of("sub", "1")),
            auth,
            "session",
            "oa",
            "https://example.test/",
            "s"));
    assertThat(session.isInvalid()).isTrue();
    assertThat(signedIn.getAttribute("auditGap")).isNull();
    var normal = new MockHttpServletResponse();
    new AuditGapResponse(normal, signedIn).sendRedirect("https://example.test/");
    assertThat(normal.getRedirectedUrl()).isEqualTo("https://example.test/");
  }

  @Test
  @DisplayName("组合内省校验器只包装目标 provider，不改变其它认证器及次序")
  void providerComposition() {
    var unrelated = mock(AuthenticationProvider.class);
    var target =
        new OAuth2TokenIntrospectionAuthenticationProvider(
            mock(
                org.springframework.security.oauth2.server.authorization.client
                    .RegisteredClientRepository.class),
            store);
    var providers = new ArrayList<AuthenticationProvider>(List.of(unrelated, target));
    ProtocolProviders.liveIntrospection(providers, identity);
    assertThat(providers).hasSize(2);
    assertThat(providers.getFirst()).isSameAs(unrelated);
    assertThat(providers.get(1)).isInstanceOf(LiveIntrospection.class);
  }
}
