package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.port.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(BearerFilter.class);
  private final JwtDecoder decoder;
  private final SessionStore sessions;
  private final ManagementStore store;

  public BearerFilter(JwtDecoder decoder, SessionStore sessions, ManagementStore store) {
    this.decoder = decoder;
    this.sessions = sessions;
    this.store = store;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      String path = request.getRequestURI();
      if (path.equals("/auth/login") || path.equals("/actuator/health")) {
        chain.doFilter(request, response);
        return;
      }
      String header = request.getHeader("Authorization");
      if (header == null || !header.startsWith("Bearer ")) throw AuthService.unauthenticated();
      Jwt token = decoder.decode(header.substring(7));
      if (token.getAudience().size() != 1
          || token.getId() == null
          || token.getExpiresAt() == null
          || !Instant.now().isBefore(token.getExpiresAt())) throw AuthService.unauthenticated();
      var session = sessions.find(token.getId());
      if (session == null
          || session.revokedAt() != null
          || !Instant.now().isBefore(session.expiresAt())
          || !Objects.equals(session.userId().toString(), token.getSubject())
          || !Objects.equals(session.systemKey(), token.getAudience().getFirst()))
        throw AuthService.unauthenticated();
      Data.User user;
      try {
        user = store.user(session.userId());
      } catch (BusinessException e) {
        throw AuthService.unauthenticated();
      }
      if (!store.permissionsForUser(user.id(), session.systemKey()).contains("login"))
        throw BusinessException.forbidden();
      var identity =
          new Data.Identity(user.id(), user.departmentId(), session.systemKey(), session.id());
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
      chain.doFilter(request, response);
    } catch (JwtException | IllegalArgumentException e) {
      error(response, 401, "UNAUTHENTICATED", "凭据无效或登录已过期");
    } catch (BusinessException e) {
      error(response, e.status(), e.code(), e.getMessage());
    } catch (DataAccessException e) {
      log.error("authentication database unavailable type={}", e.getClass().getSimpleName());
      error(response, 503, "SERVICE_UNAVAILABLE", "身份服务暂不可用");
    } finally {
      SecurityContextHolder.clearContext();
      log.debug(
          "request method={} path={} status={} elapsedMicros={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          (System.nanoTime() - start) / 1000);
    }
  }

  private void error(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    log.warn("authentication rejected status={} code={}", status, code);
  }
}
