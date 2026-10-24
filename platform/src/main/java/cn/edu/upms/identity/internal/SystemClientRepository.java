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
    // 停用系统不可参与协议登录；员工客户端还必须配置登录回调。
    if (!system.active() || (!directory && system.redirectUri() == null)) {
      return null;
    }
    // 从接入模块事实构建框架客户端，内部编号与公开标识分别赋值。
    var builder =
        RegisteredClient.withId(directory ? "directory-" + system.id() : system.id().toString())
            .clientId(system.key() + (directory ? "-directory" : ""))
            // 选择对应身份的哈希，并限定为后端 HTTP Basic 客户端认证。
            .clientSecret(directory ? system.directorySecret() : system.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            // 令牌设置随客户端生成，不另维护一套认证数据库记录。
            .tokenSettings(
                TokenSettings.builder()
                    // 授权码最多有效 60 秒，访问令牌使用不可自行解码的不透明格式。
                    .authorizationCodeTimeToLive(Duration.ofSeconds(60))
                    .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                    // 目录令牌为五分钟；员工八小时上限还会在生成时收紧至会话剩余寿命。
                    .accessTokenTimeToLive(directory ? Duration.ofMinutes(5) : Duration.ofHours(8))
                    .build());
    // 只声明本期支持的授权类型与 scope，避免目录客户端拿到员工登录能力。
    if (directory) {
      builder
          // 目录客户端只可用独立凭据申请上报范围，不具备员工授权码能力。
          .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
          .scope("directory.report");
    } else {
      builder
          // 员工客户端使用授权码，并登记精确的登录回调目标。
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri(system.redirectUri())
          // 退出回调与登录回调分别登记，openid 范围启用标准身份协议。
          .postLogoutRedirectUri(system.logoutRedirectUri())
          .scope(OidcScopes.OPENID)
          // profile 为身份资料范围；业务查看、编辑权限仍须实时鉴权。
          .scope(OidcScopes.PROFILE)
          .clientSettings(
              ClientSettings.builder()
                  // 强制 PKCE；课程中已登记客户端无需再显示一次授权同意页面。
                  .requireProofKey(true)
                  .requireAuthorizationConsent(false)
                  .build());
    }
    // 本期不登记 REFRESH_TOKEN 授权类型，框架因此不会发行刷新令牌。
    return builder.build();
  }
}
