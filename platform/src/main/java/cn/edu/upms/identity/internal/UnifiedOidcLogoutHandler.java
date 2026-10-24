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

/** 标准 RP 退出后的平台动作；即使审计故障也不能恢复已撤销会话. */
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
