package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

/**
 * 在框架标准令牌内省结果上补充本项目的动态身份条件.
 *
 * <p>先让框架校验调用客户端和令牌，再要求当前员工会话、系统归属仍有效。身份过期等业务拒绝转换为 active=false；技术不可用继续抛出，不能把服务故障伪装成正常的无效令牌结果.
 */
final class LiveIntrospection implements AuthenticationProvider {
  private final AuthenticationProvider delegate;
  private final IdentityService identity;

  LiveIntrospection(AuthenticationProvider delegate, IdentityService identity) {
    this.delegate = delegate;
    this.identity = identity;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    var result =
        (OAuth2TokenIntrospectionAuthenticationToken) delegate.authenticate(authentication);
    if (!result.getTokenClaims().isActive()) {
      return result;
    }
    var client = (OAuth2ClientAuthenticationToken) result.getPrincipal();
    try {
      identity.token(result.getToken(), client.getRegisteredClient().getClientId());
      return result;
    } catch (BusinessException failure) {
      if (failure.getStatus() >= 500) {
        throw failure;
      }
      return new OAuth2TokenIntrospectionAuthenticationToken(
          result.getToken(), client, OAuth2TokenIntrospection.builder(false).build());
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return delegate.supports(authentication);
  }
}
