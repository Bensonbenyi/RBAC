package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 校验令牌、会话绑定及目标系统登录许可. */
public class BearerFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(BearerFilter.class);
  private final JwtDecoder decoder;
  private final SessionStore sessions;
  private final ManagementStore store;
  private final EffectiveAccessStore effectiveAccess;

  /**
   * 构造请求鉴权过滤器，账号、会话及当前授权分别查询.
   *
   * @param decoder 验证并解析 JWT 的组件
   * @param sessions 查询和撤销登录会话的端口
   * @param store 管理数据读写端口
   * @param effectiveAccess 查询角色继承与有效权限的端口
   */
  public BearerFilter(
      JwtDecoder decoder,
      SessionStore sessions,
      ManagementStore store,
      EffectiveAccessStore effectiveAccess) {
    this.decoder = decoder;
    this.sessions = sessions;
    this.store = store;
    this.effectiveAccess = effectiveAccess;
  }

  /**
   * 为受保护请求验证令牌并建立当前线程身份.
   *
   * @param request 用于区分接口路径的 HTTP 请求
   * @param response 当前 HTTP 响应
   * @param chain 后续过滤器和业务请求处理链
   * @throws ServletException 请求继续交由 Servlet 链处理时失败
   * @throws IOException 读写请求或响应时失败
   */
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
      if (header == null || !header.startsWith("Bearer ")) {
        throw AuthService.unauthenticated();
      }
      Jwt token = decoder.decode(header.substring(7));
      if (token.getAudience().isEmpty() || token.getId() == null || token.getExpiresAt() == null) {
        throw AuthService.unauthenticated();
      }
      SessionStore.Session session = sessions.find(token.getId());
      if (session == null
          || session.revokedAt() != null
          || !Instant.now().isBefore(session.expiresAt())
          || !Objects.equals(session.userId().toString(), token.getSubject())) {
        throw AuthService.unauthenticated();
      }
      Data.User user;
      try {
        user = store.user(session.userId());
      } catch (BusinessException e) {
        throw AuthService.unauthenticated();
      }
      String target = request.getHeader("X-Target-System");
      if (target == null) {
        target = token.getAudience().getFirst();
      }
      // 登录许可被撤销后，持有该令牌的用户仍可结束会话。
      boolean logout = path.equals("/auth/logout") && request.getMethod().equals("POST");
      if (!token.getAudience().contains(target)
          || (!logout
              && !effectiveAccess.permissionsForUser(user.id(), target).contains("login"))) {
        throw BusinessException.forbidden();
      }
      Data.Identity identity =
          new Data.Identity(user.id(), user.departmentId(), target, session.id());
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
      chain.doFilter(request, response);
    } catch (JwtException | IllegalArgumentException e) {
      error(response, 401, "UNAUTHORIZED", "凭据无效或登录已过期");
    } catch (BusinessException e) {
      error(response, e.status(), e.code(), e.getMessage());
    } catch (DataAccessException e) {
      String code =
          request.getRequestURI().startsWith("/auth/")
              ? "IDENTITY_SERVICE_UNAVAILABLE"
              : "SERVICE_UNAVAILABLE";
      error(response, 503, code, "身份服务暂不可用");
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

  /**
   * 写入与鉴权失败对应的 JSON 响应.
   *
   * @param response 当前 HTTP 响应
   * @param status 对外返回的 HTTP 状态码
   * @param code 提供给客户端的业务错误码
   * @param message 提供给调用方的错误说明
   * @throws IOException 向客户端写入响应失败
   */
  private void error(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }
}
