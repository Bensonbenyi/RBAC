package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在安全上下文加载后复核统一会话，落实固定八小时期限及停用失效.
 *
 * <p>容器 HttpSession 通常按空闲时间延长，不能单独满足固定绝对期限。身份无效时同时清理本应用会话与线程安全上下文，后续过滤器会按未登录处理；这里继续过滤链，不自行返回成功。
 * getSession(false) 避免为本来没有会话的请求创建新会话.
 */
final class AbsoluteSessionFilter extends OncePerRequestFilter {
  private final IdentityService identity;

  AbsoluteSessionFilter(IdentityService identity) {
    this.identity = identity;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    UnifiedPrincipal user = IdentityService.user(authentication);
    if (user != null) {
      try {
        identity.requireUser(user);
      } catch (BusinessException expired) {
        var session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
