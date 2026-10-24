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

/**
 * 将框架保存的认证结果转换成业务可信身份，并复核当前会话与员工状态.
 *
 * <p>browser 处理平台 HttpSession 中的 principal；token 处理模拟系统后端提交的不透明访问令牌； directory
 * 只处理独立系统凭据获得的目录令牌。三条入口互不替代，验证成功后也不代表有业务权限.
 *
 * <p>访问令牌虽未到期，所依赖会话仍可能因统一退出或员工停用而失效，因此不能只看令牌时间。这里只返回员工和系统事实，允许或拒绝具体动作由授权模块负责.
 */
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
    // 员工令牌必须来自授权码流程，目录 client_credentials 令牌不能冒充某个员工。
    if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(
        authorization.getAuthorizationGrantType())) {
      throw rejected();
    }
    var system = integration.system(Long.parseLong(authorization.getRegisteredClientId()));
    // expectedSystem 来自请求，但真实 systemId 来自令牌登记关系；两者必须相等。
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
    // 从可信认证上下文提取统一主体，不能从浏览器参数选择要退出的会话。
    UnifiedPrincipal user = user(principal);
    // 没有统一主体时按无须额外撤销处理，保持退出可重复执行。
    if (user == null) {
      return true;
    }
    // 先撤销会话，再尝试审计；审计失败不可能恢复已经退出的会话。
    sessions.end(user.sessionId());
    try {
      // 读取员工当前部门用于审计；会话已经撤销，后续查询失败也不恢复。
      var employee = organization.employee(user.employeeId());
      // 正常路径记录当前会话退出，其他浏览器的独立会话不受影响。
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
      // true 表示会话处理和审计均完成，页面可展示正常退出提示。
      return true;
    } catch (RuntimeException failure) {
      // 审计或资料读取出错则暂停后续受控操作，保留已退出的事实。
      audit.pause();
      // false 用于向退出回调报告审计缺口，不表示允许旧会话继续使用。
      return false;
    }
  }

  /** 每次使用身份时复核内存会话和员工启用状态，防止旧认证结果继续通行. */
  void requireUser(UnifiedPrincipal user) {
    if (!sessions.valid(user) || !organization.employee(user.employeeId()).active()) {
      throw rejected();
    }
  }

  /**
   * 从服务端存储定位访问令牌，并检查它仍有效且确为 access token.
   *
   * <p>存储索引也可能按授权码或 ID Token 找到同一个授权对象，所以必须核对访问令牌原值，不能把"能查到授权对象"当成"提交的是有效访问令牌".
   */
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
