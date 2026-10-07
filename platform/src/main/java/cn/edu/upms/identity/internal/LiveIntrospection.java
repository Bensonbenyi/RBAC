package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

/** 标准内省在框架验证后补上统一会话与客户端归属，退出后不能继续报告 active. */
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
