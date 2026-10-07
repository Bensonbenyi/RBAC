package cn.edu.upms.identity.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** 课程身份校验适配器；用户名与手机号同时匹配，不模拟密码认证强度. */
@Component
class DemoAuthenticationProvider implements AuthenticationProvider {
  private final OrganizationApi organization;
  private final AuditApi audit;
  private final UnifiedSessions sessions;

  DemoAuthenticationProvider(
      OrganizationApi organization, AuditApi audit, UnifiedSessions sessions) {
    this.organization = organization;
    this.audit = audit;
    this.sessions = sessions;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    try {
      audit.requireAvailable();
      OrganizationApi.Employee employee;
      try {
        employee =
            organization.authenticate(
                authentication.getName(), String.valueOf(authentication.getCredentials()));
      } catch (BusinessException invalid) {
        audit.record(
            new AuditApi.Event(
                null,
                null,
                null,
                null,
                "LOGIN_FAILED",
                "demo-identity",
                "DENY",
                "用户名与手机号校验失败或身份停用"));
        throw new BadCredentialsException("用户名、手机号不匹配或员工已停用");
      }
      UnifiedPrincipal principal = sessions.open(employee.id());
      try {
        // 登录成功事件必须先可靠提交，才把认证结果交回安全框架。
        audit.record(
            new AuditApi.Event(
                employee.id(),
                employee.departmentId(),
                null,
                null,
                "LOGIN_SUCCEEDED",
                "session:" + principal.sessionId(),
                "SUCCESS",
                "统一会话固定八小时"));
      } catch (RuntimeException failure) {
        sessions.end(principal.sessionId());
        throw failure;
      }
      return UsernamePasswordAuthenticationToken.authenticated(
          principal, null, List.of(new SimpleGrantedAuthority("AUTHENTICATED_EMPLOYEE")));
    } catch (BusinessException unavailable) {
      throw new AuthenticationServiceException("认证服务暂不可用", unavailable);
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
