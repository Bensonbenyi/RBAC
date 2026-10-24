package cn.edu.upms.identity.internal;

import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Duration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * 将接入模块的系统事实适配成框架 RegisteredClient，不另建一套客户端表.
 *
 * <p>同一系统映射两种客户端：员工登录客户端使用授权码和 PKCE，目录客户端使用独立凭据及 client_credentials。框架内部 id 采用系统数据库编号，公开 clientId
 * 采用稳定 systemKey，目录身份分别带不同前后缀；读代码时不要把这两个编号混作同一种标识.
 */
@Component
class SystemClientRepository implements RegisteredClientRepository {
  private final IntegrationApi integration;

  SystemClientRepository(IntegrationApi integration) {
    this.integration = integration;
  }

  @Override
  public void save(RegisteredClient client) {
    throw new UnsupportedOperationException("客户端只能通过系统接入用例管理");
  }

  @Override
  public RegisteredClient findById(String id) {
    try {
      boolean directory = id.startsWith("directory-");
      long systemId = Long.parseLong(directory ? id.substring(10) : id);
      return adapt(integration.system(systemId), directory);
    } catch (BusinessException | NumberFormatException notFound) {
      return null;
    }
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    try {
      boolean directory = clientId.endsWith("-directory");
      String key = directory ? clientId.substring(0, clientId.length() - 10) : clientId;
      return adapt(integration.byKey(key), directory);
    } catch (BusinessException notFound) {
      return null;
    }
  }

  private static RegisteredClient adapt(IntegrationApi.SystemFact system, boolean directory) {
    if (!system.active() || (!directory && system.redirectUri() == null)) {
      return null;
    }
    var builder =
        RegisteredClient.withId(directory ? "directory-" + system.id() : system.id().toString())
            .clientId(system.key() + (directory ? "-directory" : ""))
            .clientSecret(directory ? system.directorySecret() : system.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .tokenSettings(
                TokenSettings.builder()
                    .authorizationCodeTimeToLive(Duration.ofSeconds(60))
                    .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                    .accessTokenTimeToLive(directory ? Duration.ofMinutes(5) : Duration.ofHours(8))
                    .build());
    // 只声明本期支持的授权类型与 scope，避免目录客户端拿到员工登录能力。
    if (directory) {
      builder
          .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
          .scope("directory.report");
    } else {
      builder
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri(system.redirectUri())
          .postLogoutRedirectUri(system.logoutRedirectUri())
          .scope(OidcScopes.OPENID)
          .scope(OidcScopes.PROFILE)
          .clientSettings(
              ClientSettings.builder()
                  .requireProofKey(true)
                  .requireAuthorizationConsent(false)
                  .build());
    }
    // 本期不登记 REFRESH_TOKEN 授权类型，框架因此不会发行刷新令牌。
    return builder.build();
  }
}
