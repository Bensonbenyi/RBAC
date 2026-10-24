package cn.edu.upms.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 限制同时进入后台写用例的请求数量，为实时鉴权保留数据库连接.
 *
 * <p>管理写入会竞争粗粒度治理锁；若大量线程先拿连接再排队，会拖慢无需该锁的鉴权读。本过滤器在 Controller 及业务事务之前用公平 Semaphore 只放入四个写请求，最多等两秒。
 * 它控制并发量，不负责鉴权，也不是每秒请求数限制；真正身份和范围仍由后续链路校验.
 */
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
      // 恢复中断标记，让外层容器或执行器仍能感知取消；finally 仅归还实际取得的许可。
      Thread.currentThread().interrupt();
      response.sendError(503, "管理请求中断，请重试");
    } finally {
      if (acquired) {
        writers.release();
      }
    }
  }
}
