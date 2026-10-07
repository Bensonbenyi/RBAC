package cn.edu.upms.identity.internal;

import cn.edu.upms.shared.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 单节点课程会话存储；绝对寿命八小时，重启全部失效，访问不会续期. */
@Component
class UnifiedSessions {
  private final ConcurrentHashMap<String, UnifiedPrincipal> sessions = new ConcurrentHashMap<>();
  private final Clock clock;

  UnifiedSessions(Clock clock) {
    this.clock = clock;
  }

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

  boolean valid(UnifiedPrincipal principal) {
    return principal != null
        && clock.instant().isBefore(principal.expiresAt())
        && principal.equals(sessions.get(principal.sessionId()));
  }

  void end(String sessionId) {
    sessions.remove(sessionId);
  }

  void endEmployee(long employeeId) {
    sessions.entrySet().removeIf(e -> e.getValue().employeeId() == employeeId);
  }

  /** 清理到期记录，不修改有效会话的创建时间或到期时间. */
  @Scheduled(fixedDelay = 60000)
  public void expire() {
    sessions.entrySet().removeIf(e -> !clock.instant().isBefore(e.getValue().expiresAt()));
  }
}
