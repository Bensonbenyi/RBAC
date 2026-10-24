package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 保存本进程统一会话的绝对期限与撤销状态.
 *
 * <p>统一会话编号与 Servlet 容器的 HttpSession 编号承担不同职责：前者跨模拟系统关联登录，
 * 后者承载当前应用浏览器会话。每次登录生成新统一编号，同一员工可有多个独立浏览器会话.
 *
 * <p>ConcurrentHashMap 保证单次存取的线程安全；过期检查在 valid 中即时执行，定时任务只回收内存。容量判断是软保护，并发 open 的复合步骤不构成严格的 30,000
 * 条配额.
 */
@Component
class UnifiedSessions {
  private final ConcurrentHashMap<String, UnifiedPrincipal> sessions = new ConcurrentHashMap<>();
  private final Clock clock;

  UnifiedSessions(Clock clock) {
    this.clock = clock;
  }

  /** 新建不可变身份，固定到期时间；后续读取不会重写这个时间. */
  UnifiedPrincipal open(long employeeId) {
    if (sessions.size() >= 30000) {
      throw BusinessException.unavailable();
    }
    UnifiedPrincipal principal =
        new UnifiedPrincipal(
            employeeId, UUID.randomUUID().toString(), clock.instant().plus(Duration.ofHours(8)));
    sessions.put(principal.sessionId(), principal);
    return principal;
  }

  /** 同时检查期限和登记状态：未过期但已从 Map 移除的会话也必须无效. */
  boolean valid(UnifiedPrincipal principal) {
    return principal != null
        && clock.instant().isBefore(principal.expiresAt())
        && principal.equals(sessions.get(principal.sessionId()));
  }

  void end(String sessionId) {
    sessions.remove(sessionId);
  }

  /** 员工停用后的提交回调使用此能力，结束该员工所有浏览器的统一会话. */
  void endEmployee(long employeeId) {
    sessions.entrySet().removeIf(e -> e.getValue().employeeId() == employeeId);
  }

  /** 清理到期记录，不修改有效会话的创建时间或到期时间. */
  @Scheduled(fixedDelay = 60000)
  public void expire() {
    sessions.entrySet().removeIf(e -> !clock.instant().isBefore(e.getValue().expiresAt()));
  }
}
