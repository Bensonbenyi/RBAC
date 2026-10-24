package cn.edu.upms.identity.internal;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;

/**
 * 认证后保存的不可变员工身份，随当前会话传递，不存于共享 Service 字段.
 *
 * <p>getName 返回稳定员工编号字符串，成为 OIDC sub；用户名可修改，不能用作稳定关联键。同一员工不同浏览器有不同 sessionId；expiresAt
 * 在创建时固定，不因访问延长.
 */
record UnifiedPrincipal(long employeeId, String sessionId, Instant expiresAt)
    implements Principal, Serializable {
  @Override
  public String getName() {
    return String.valueOf(employeeId);
  }
}
