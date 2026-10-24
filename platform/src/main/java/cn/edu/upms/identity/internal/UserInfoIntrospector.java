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

/**
 * 把不透明令牌适配成 UserInfo 资源链需要的 OAuth2AuthenticatedPrincipal.
 *
 * <p>校验服务端访问令牌状态，并对存在员工身份的授权结果复核统一会话。随后按 scope 生成框架权限，例如 openid 对应 SCOPE_openid，由资源链执行访问限制。scope
 * 是协议能力，不等同于 OA 的查看、编辑等业务权限；此处不生成业务角色权限.
 */
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
