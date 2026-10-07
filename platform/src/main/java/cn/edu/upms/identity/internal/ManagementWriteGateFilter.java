package cn.edu.upms.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;

/** 管理写请求进入用例事务前限流，避免大量治理锁等待者占满数据库连接而饿死鉴权. */
final class ManagementWriteGateFilter extends OncePerRequestFilter {
  private final Semaphore writers = new Semaphore(4, true);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith("/api/admin/")
        || !"POST".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean acquired = false;
    try {
      // 等待时尚未进入任何业务事务，不占 JDBC 连接；有限等待后明确技术失败。
      acquired = writers.tryAcquire(2, TimeUnit.SECONDS);
      if (!acquired) {
        response.setStatus(503);
        response.setContentType("application/problem+json;charset=UTF-8");
        response
            .getWriter()
            .write("{\"status\":503,\"code\":\"MANAGEMENT_BUSY\",\"detail\":\"管理写入繁忙，请稍后重新确认\"}");
        return;
      }
      chain.doFilter(request, response);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      response.sendError(503, "管理请求中断，请重试");
    } finally {
      if (acquired) {
        writers.release();
      }
    }
  }
}
