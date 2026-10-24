package cn.edu.upms.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.web.util.UriComponentsBuilder;

/** 保留框架已校验的退出回调，仅附加可见的审计缺口标识. */
final class AuditGapResponse extends HttpServletResponseWrapper {
  private final HttpServletRequest request;

  AuditGapResponse(HttpServletResponse response, HttpServletRequest request) {
    super(response);
    this.request = request;
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    String target =
        Boolean.TRUE.equals(request.getAttribute("auditGap"))
            ? UriComponentsBuilder.fromUriString(location)
                .queryParam("audit", "failed")
                .build(true)
                .toUriString()
            : location;
    super.sendRedirect(target);
  }
}
