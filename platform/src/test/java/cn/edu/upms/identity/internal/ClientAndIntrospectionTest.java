package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.shared.BusinessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** 客户端适配保留业务系统与目录身份的隔离，内省不把技术故障伪装为有效令牌. */
class ClientAndIntrospectionTest {
  @Test
  @DisplayName("系统登记是客户端唯一来源，目录身份无需浏览器回调且不发行刷新令牌")
  void clientRepository() {
    var integration = mock(IntegrationApi.class);
    var repository = new SystemClientRepository(integration);
    when(integration.system(1)).thenReturn(system(true, "http://localhost/cb"));
    when(integration.byKey("oa")).thenReturn(system(true, "http://localhost/cb"));
    assertThat(repository.findById("1").getClientId()).isEqualTo("oa");
    assertThat(repository.findByClientId("oa").getAuthorizationGrantTypes())
        .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    assertThat(repository.findById("directory-1").getScopes()).containsExactly("directory.report");
    assertThat(repository.findByClientId("oa-directory").getClientId()).isEqualTo("oa-directory");
    assertThat(repository.findById("bad-id")).isNull();
    when(integration.system(1)).thenThrow(new BusinessException(404, "UNKNOWN", "未登记"));
    assertThat(repository.findById("1")).isNull();
    when(integration.byKey("unknown")).thenThrow(new BusinessException(404, "UNKNOWN", "未登记"));
    assertThat(repository.findByClientId("unknown")).isNull();
    when(integration.byKey("oa")).thenReturn(system(false, "http://localhost/cb"));
    assertThat(repository.findByClientId("oa")).isNull();
    when(integration.byKey("oa")).thenReturn(system(true, null));
    assertThat(repository.findByClientId("oa")).isNull();
    assertThat(repository.findByClientId("oa-directory")).isNotNull();
    assertThatThrownBy(() -> repository.save(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("标准内省保留框架拒绝，补查会话后只将身份失效转为 inactive")
  void liveIntrospection() {
    var delegate = mock(AuthenticationProvider.class);
    var identity = mock(IdentityService.class);
    var adapter = new LiveIntrospection(delegate, identity);
    var client =
        RegisteredClient.withId("1")
            .clientId("oa")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb")
            .build();
    var authentication =
        new OAuth2ClientAuthenticationToken(
            client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
    var request =
        new OAuth2TokenIntrospectionAuthenticationToken(
            "token", authentication, OAuth2TokenIntrospection.builder(false).build());
    var inactive =
        new OAuth2TokenIntrospectionAuthenticationToken(
            "token", authentication, OAuth2TokenIntrospection.builder(false).build());
    when(delegate.authenticate(request)).thenReturn(inactive);
    assertThat(adapter.authenticate(request)).isSameAs(inactive);
    var active =
        new OAuth2TokenIntrospectionAuthenticationToken(
            "token", authentication, OAuth2TokenIntrospection.builder(true).build());
    when(delegate.authenticate(request)).thenReturn(active);
    assertThat(adapter.authenticate(request)).isSameAs(active);
    when(identity.token("token", "oa")).thenThrow(new BusinessException(401, "EXPIRED", "失效"));
    assertThat(
            ((OAuth2TokenIntrospectionAuthenticationToken) adapter.authenticate(request))
                .getTokenClaims()
                .isActive())
        .isFalse();
    doThrow(BusinessException.unavailable()).when(identity).token("token", "oa");
    assertThatThrownBy(() -> adapter.authenticate(request)).isInstanceOf(BusinessException.class);
    when(delegate.supports(String.class)).thenReturn(false);
    assertThat(adapter.supports(String.class)).isFalse();
  }

  @Test
  @DisplayName("凭据空值和密码学提供者故障均拒绝，不缓存失败")
  void secretFailure() {
    var verifier = new ClientSecretVerifier();
    assertThat(verifier.matches(null, "hash")).isFalse();
    assertThat(verifier.matches("raw", null)).isFalse();
    try (var digest = mockStatic(MessageDigest.class)) {
      digest
          .when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("提供者故障"));
      assertThatThrownBy(() -> verifier.matches("raw", "hash"))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(NoSuchAlgorithmException.class);
    }
  }

  private static IntegrationApi.SystemFact system(boolean active, String callback) {
    return new IntegrationApi.SystemFact(
        1L, "oa", "办公", active, "hash", "hash", callback, "http://localhost", 1, 0, Set.of(1L));
  }
}
