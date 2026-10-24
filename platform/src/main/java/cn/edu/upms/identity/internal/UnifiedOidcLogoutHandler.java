package cn.edu.upms.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

/**
 * 衔接框架已经验证的 RP 退出请求与平台统一会话撤销.
 *
 * <p>框架先校验 ID Token hint 和登记退出回调；这里通过 hint 找到原认证身份，撤销其统一会话。若当前平台浏览器身份也经过框架确认，再清理本地 SecurityContext
 * 与 HttpSession。审计失败仅附加提示标记，不阻止已经发生的会话撤销.
 */
final class UnifiedOidcLogoutHandler implements LogoutHandler {
  private final OAuth2AuthorizationService authorizations;
  private final IdentityService identity;

  UnifiedOidcLogoutHandler(OAuth2AuthorizationService authorizations, IdentityService identity) {
    this.authorizations = authorizations;
    this.identity = identity;
  }

  @Override
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    var oidc = (OidcLogoutAuthenticationToken) authentication;
    var authorization =
        authorizations.findByToken(
            oidc.getIdTokenHint(), new OAuth2TokenType(OidcParameterNames.ID_TOKEN));
    Principal user =
        authorization == null ? null : authorization.getAttribute(Principal.class.getName());
    if (!identity.logout(user)) {
      request.setAttribute("auditGap", true);
    }
    if (oidc.isPrincipalAuthenticated()) {
      new SecurityContextLogoutHandler()
          .logout(request, response, (Authentication) oidc.getPrincipal());
    }
  }
}
