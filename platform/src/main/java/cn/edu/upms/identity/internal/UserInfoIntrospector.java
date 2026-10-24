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
    // UserInfo 也是受控请求，先检查审计可用状态。
    audit.requireAvailable();
    // 按访问令牌索引查找服务端授权对象，不尝试从不透明令牌中解码身份。
    var authorization = authorizations.findByToken(value, OAuth2TokenType.ACCESS_TOKEN);
    // 不存在、缺少访问令牌或访问令牌过期及撤销时，统一作为无效令牌拒绝。
    if (authorization == null
        || authorization.getAccessToken() == null
        || !authorization.getAccessToken().isActive()) {
      throw new BadOpaqueTokenException("令牌无效");
    }
    // 从服务端保存的认证属性提取员工主体，客户端不能自行声明员工编号。
    UnifiedPrincipal user =
        IdentityService.user(authorization.getAttribute(Principal.class.getName()));
    // 存在员工主体时再核对会话和员工状态；目录客户端不产生员工身份。
    if (user != null) {
      try {
        // 即使令牌期限未到，统一退出或员工停用也应立即使此请求失败。
        identity.requireUser(user);
      } catch (BusinessException expired) {
        // 将项目会话拒绝转成资源服务器可识别的令牌异常。
        throw new BadOpaqueTokenException("会话失效");
      }
    }
    // 复制服务端保存的声明，避免修改授权存储中的原始 Map。
    var claims = new HashMap<>(authorization.getAccessToken().getClaims());
    // 把 OAuth scope 映射为 Spring 权限标记，供资源链检查 SCOPE_openid。
    var authorities =
        authorization.getAccessToken().getToken().getScopes().stream()
            .<org.springframework.security.core.GrantedAuthority>map(
                scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
            .toList();
    // 适配后的主体交回资源链；其中不包含可替代实时鉴权的业务角色结论。
    return new DefaultOAuth2AuthenticatedPrincipal(
        authorization.getPrincipalName(), claims, authorities);
  }
}
