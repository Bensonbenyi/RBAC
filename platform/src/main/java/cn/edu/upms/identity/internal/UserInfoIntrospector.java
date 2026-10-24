package cn.edu.upms.identity.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.shared.BusinessException;
import java.security.Principal;
import java.util.HashMap;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/** 将框架资源端点与当前会话事实衔接；无效令牌统一转换为标准 Bearer 拒绝. */
final class UserInfoIntrospector implements OpaqueTokenIntrospector {
  private final OAuth2AuthorizationService authorizations;
  private final IdentityService identity;
  private final AuditApi audit;

  UserInfoIntrospector(
      OAuth2AuthorizationService authorizations, IdentityService identity, AuditApi audit) {
    this.authorizations = authorizations;
    this.identity = identity;
    this.audit = audit;
  }

  @Override
  public OAuth2AuthenticatedPrincipal introspect(String value) {
    audit.requireAvailable();
    var authorization = authorizations.findByToken(value, OAuth2TokenType.ACCESS_TOKEN);
    if (authorization == null
        || authorization.getAccessToken() == null
        || !authorization.getAccessToken().isActive()) {
      throw new BadOpaqueTokenException("令牌无效");
    }
    UnifiedPrincipal user =
        IdentityService.user(authorization.getAttribute(Principal.class.getName()));
    if (user != null) {
      try {
        identity.requireUser(user);
      } catch (BusinessException expired) {
        throw new BadOpaqueTokenException("会话失效");
      }
    }
    var claims = new HashMap<>(authorization.getAccessToken().getClaims());
    var authorities =
        authorization.getAccessToken().getToken().getScopes().stream()
            .<org.springframework.security.core.GrantedAuthority>map(
                scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
            .toList();
    return new DefaultOAuth2AuthenticatedPrincipal(
        authorization.getPrincipalName(), claims, authorities);
  }
}
