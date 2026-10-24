package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cn.edu.upms.audit.api.AuditApi;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;

/** 检查标准框架生成令牌的会话期限，身份令牌不承载业务权限. */
class TokenLifetimeTest {
  @Test
  @DisplayName("ACC-F-003/005 ID Token 的框架期限不得越过统一会话，不透明访问令牌也受相同上限")
  void lifetime() throws Exception {
    var configuration = new SecurityConfiguration();
    var identity = mock(IdentityService.class);
    var audit = mock(AuditApi.class);
    var generator = configuration.tokenGenerator(configuration.jwkSource(), identity, audit);
    Instant near = Instant.now().plusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    var user = new UnifiedPrincipal(1, "near-session", near);
    var principal = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    var context = context(principal, OAuth2TokenFormat.REFERENCE);
    OAuth2Token id =
        generator.generate(
            context.tokenType(new OAuth2TokenType(OidcParameterNames.ID_TOKEN)).build());
    assertThat(id).isInstanceOf(Jwt.class);
    assertThat(id.getExpiresAt()).isEqualTo(near);
    assertThat(((Jwt) id).getClaims()).doesNotContainKeys("permissions", "roles");
    OAuth2Token access =
        generator.generate(context.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
    assertThat(access).isInstanceOf(OAuth2AccessToken.class);
    assertThat(access.getExpiresAt()).isEqualTo(near);
    verify(audit).requireAvailable();
    var far = new UnifiedPrincipal(1, "far-session", Instant.now().plusSeconds(8 * 3600));
    var other = UsernamePasswordAuthenticationToken.authenticated(far, null, List.of());
    var ordinary =
        generator.generate(
            context(other, OAuth2TokenFormat.REFERENCE)
                .tokenType(new OAuth2TokenType(OidcParameterNames.ID_TOKEN))
                .build());
    assertThat(ordinary.getExpiresAt()).isBefore(far.expiresAt());
    // 自包含访问令牌属于框架可选路径，本平台客户端只登记不透明格式。
    var jwtAccess =
        generator.generate(
            context(principal, OAuth2TokenFormat.SELF_CONTAINED)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build());
    assertThat(jwtAccess).isInstanceOf(Jwt.class);
    var system = UsernamePasswordAuthenticationToken.authenticated("directory", null, List.of());
    assertThat(
            generator.generate(
                context(system, OAuth2TokenFormat.REFERENCE)
                    .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                    .build()))
        .isNotNull();
    assertThat(
            generator.generate(
                context(system, OAuth2TokenFormat.REFERENCE)
                    .tokenType(new OAuth2TokenType(OidcParameterNames.ID_TOKEN))
                    .build()))
        .isNotNull();
  }

  private DefaultOAuth2TokenContext.Builder context(
      org.springframework.security.core.Authentication principal, OAuth2TokenFormat format) {
    var client =
        RegisteredClient.withId("client")
            .clientId("oa")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb")
            .scope("openid")
            .tokenSettings(TokenSettings.builder().accessTokenFormat(format).build())
            .build();
    var settings = AuthorizationServerSettings.builder().issuer("https://example.test").build();
    AuthorizationServerContext server =
        new AuthorizationServerContext() {
          public String getIssuer() {
            return settings.getIssuer();
          }

          public AuthorizationServerSettings getAuthorizationServerSettings() {
            return settings;
          }
        };
    return DefaultOAuth2TokenContext.builder()
        .registeredClient(client)
        .principal(principal)
        .authorization(
            org.springframework.security.oauth2.server.authorization.OAuth2Authorization
                .withRegisteredClient(client)
                .principalName(principal.getName())
                .attribute(
                    org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
                        .class
                        .getName(),
                    org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
                        .authorizationCode()
                        .authorizationUri("https://example.test/oauth2/authorize")
                        .clientId("oa")
                        .redirectUri("http://localhost/cb")
                        .scopes(Set.of("openid"))
                        .build())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build())
        .authorizationServerContext(server)
        .authorizedScopes(Set.of("openid"))
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrant(UsernamePasswordAuthenticationToken.unauthenticated("code", ""));
  }
}
