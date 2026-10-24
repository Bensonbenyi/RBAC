package cn.edu.upms.identity.internal;

import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;

/** 仅包装框架的令牌内省能力，列表中的其它协议校验器保持原样及顺序. */
final class ProtocolProviders {
  private ProtocolProviders() {}

  static void liveIntrospection(List<AuthenticationProvider> providers, IdentityService identity) {
    for (int i = 0; i < providers.size(); i++) {
      if (providers.get(i) instanceof OAuth2TokenIntrospectionAuthenticationProvider) {
        providers.set(i, new LiveIntrospection(providers.get(i), identity));
      }
    }
  }
}
