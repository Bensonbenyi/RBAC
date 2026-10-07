package cn.edu.upms.identity.internal;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;

/** 不可变认证身份；每次浏览器登录具有独立会话编号，单例 Bean 不持有请求身份. */
record UnifiedPrincipal(long employeeId, String sessionId, Instant expiresAt)
    implements Principal, Serializable {
  @Override
  public String getName() {
    return String.valueOf(employeeId);
  }
}
