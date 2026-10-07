package cn.edu.upms.identity.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;

/** 统一检查会话、员工状态及令牌所属系统，不接受 ID Token 中的业务授权. */
@Service
public class IdentityService implements IdentityApi {
  private final UnifiedSessions sessions;
  private final OAuth2AuthorizationService authorizations;
  private final OrganizationApi organization;
  private final IntegrationApi integration;
  private final AuditApi audit;

  /** 注入身份存储及其它模块的公开能力. */
  public IdentityService(
      UnifiedSessions sessions,
      OAuth2AuthorizationService authorizations,
      OrganizationApi organization,
      IntegrationApi integration,
      AuditApi audit) {
    this.sessions = sessions;
    this.authorizations = authorizations;
    this.organization = organization;
    this.integration = integration;
    this.audit = audit;
  }

  @Override
  public SessionIdentity browser(Principal principal) {
    UnifiedPrincipal user = user(principal);
    requireUser(user);
    return new SessionIdentity(user.employeeId(), user.sessionId(), user.expiresAt());
  }

  @Override
  public TokenIdentity token(String token, String expectedSystem) {
    audit.requireAvailable();
    OAuth2Authorization authorization = access(token);
    if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(
        authorization.getAuthorizationGrantType())) {
      throw rejected();
    }
    var system = integration.system(Long.parseLong(authorization.getRegisteredClientId()));
    if (!system.active() || !system.key().equals(expectedSystem)) {
      throw BusinessException.forbidden();
    }
    UnifiedPrincipal user = user(authorization.getAttribute(Principal.class.getName()));
    requireUser(user);
    return new TokenIdentity(
        user.employeeId(),
        system.id(),
        user.sessionId(),
        authorization.getAccessToken().getToken().getExpiresAt());
  }

  @Override
  public long directory(String token) {
    audit.requireAvailable();
    OAuth2Authorization authorization = access(token);
    if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())
        || !authorization.getAccessToken().getToken().getScopes().contains("directory.report")
        || !authorization.getRegisteredClientId().startsWith("directory-")) {
      throw BusinessException.forbidden();
    }
    long systemId = Long.parseLong(authorization.getRegisteredClientId().substring(10));
    if (!integration.system(systemId).active()) {
      throw BusinessException.forbidden();
    }
    return systemId;
  }

  @Override
  public void revokeEmployeeSessions(long employeeId) {
    sessions.endEmployee(employeeId);
  }

  @Override
  public boolean logout(Principal principal) {
    UnifiedPrincipal user = user(principal);
    if (user == null) {
      return true;
    }
    // 先撤销会话，再尝试审计；审计失败不可能恢复已经退出的会话。
    sessions.end(user.sessionId());
    try {
      var employee = organization.employee(user.employeeId());
      audit.record(
          new AuditApi.Event(
              user.employeeId(),
              employee.departmentId(),
              null,
              null,
              "LOGOUT",
              "session:" + user.sessionId(),
              "SUCCESS",
              "仅结束当前统一会话"));
      return true;
    } catch (RuntimeException failure) {
      audit.pause();
      return false;
    }
  }

  void requireUser(UnifiedPrincipal user) {
    if (!sessions.valid(user) || !organization.employee(user.employeeId()).active()) {
      throw rejected();
    }
  }

  private OAuth2Authorization access(String token) {
    OAuth2Authorization authorization =
        authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
    if (authorization == null
        || authorization.getAccessToken() == null
        || !authorization.getAccessToken().isActive()
        || !authorization.getAccessToken().getToken().getTokenValue().equals(token)) {
      throw rejected();
    }
    return authorization;
  }

  static UnifiedPrincipal user(Object principal) {
    if (principal instanceof Authentication authentication) {
      return user(authentication.getPrincipal());
    }
    return principal instanceof UnifiedPrincipal user ? user : null;
  }

  private static BusinessException rejected() {
    return new BusinessException(401, "IDENTITY_EXPIRED", "身份、令牌或统一会话已失效，请重新登录");
  }
}
