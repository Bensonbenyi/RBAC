package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证事务前排队边界：并发超限明确失败，异常不能泄漏许可或阻挡读取. */
class ManagementWriteGateFilterTest {
  @Test
  @DisplayName("ACC-P-004 管理写入最多四项进入事务，饱和拒绝但读取不等待；异常释放许可")
  void boundedWritersAndExceptionRelease() throws Exception {
    var filter = new ManagementWriteGateFilter();
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request("POST"),
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      throw new ServletException("模拟事务失败");
                    }))
        .isInstanceOf(ServletException.class);
    var entered = new CountDownLatch(4);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 4; i++) {
        tasks.add(
            executor.submit(
                () -> {
                  try {
                    filter.doFilter(
                        request("POST"),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                          entered.countDown();
                          try {
                            if (!release.await(10, TimeUnit.SECONDS)) {
                              throw new ServletException("测试未释放请求");
                            }
                          } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new ServletException(interrupted);
                          }
                        });
                  } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                  }
                }));
      }
      try {
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        var read = new MockHttpServletResponse();
        filter.doFilter(request("GET"), read, (request, response) -> response.setContentType("ok"));
        assertThat(read.getContentType()).isEqualTo("ok");
        var saturated = new MockHttpServletResponse();
        filter.doFilter(
            request("POST"),
            saturated,
            (request, response) -> {
              throw new ServletException("第五项不应进入事务");
            });
        assertThat(saturated.getStatus()).isEqualTo(503);
        assertThat(saturated.getContentAsString()).contains("MANAGEMENT_BUSY");
      } finally {
        release.countDown();
      }
      for (var task : tasks) {
        task.get(3, TimeUnit.SECONDS);
      }
    }
    var restored = new MockHttpServletResponse();
    filter.doFilter(
        request("POST"), restored, (request, response) -> response.setContentType("ok"));
    assertThat(restored.getContentType()).isEqualTo("ok");
  }

  private MockHttpServletRequest request(String method) {
    var request = new MockHttpServletRequest(method, "/api/admin/employees");
    request.setServletPath("/api/admin/employees");
    return request;
  }
}
