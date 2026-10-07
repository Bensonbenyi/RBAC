package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在安全上下文加载后检查绝对会话期限，阻止容器空闲期限造成滑动续期. */
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
