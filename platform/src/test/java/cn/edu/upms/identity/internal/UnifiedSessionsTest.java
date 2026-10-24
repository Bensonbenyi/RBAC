package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 可控时钟检查八小时绝对寿命，测试访问不续期，不等待真实时间. */
class UnifiedSessionsTest {
  @Test
  @DisplayName("会话容量有界，已撤销或伪造的会话不能通过；清理保留未到期会话")
  void boundedAndRevoked() {
    var clock = new MovingClock();
    var sessions = new UnifiedSessions(clock);
    var first = sessions.open(1);
    final var second = sessions.open(2);
    assertThat(sessions.valid(null)).isFalse();
    assertThat(sessions.valid(new UnifiedPrincipal(1, "unknown", first.expiresAt()))).isFalse();
    sessions.expire();
    assertThat(sessions.valid(first)).isTrue();
    sessions.endEmployee(1);
    assertThat(sessions.valid(first)).isFalse();
    assertThat(sessions.valid(second)).isTrue();
    for (int i = 0; i < 29999; i++) {
      sessions.open(3);
    }
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> sessions.open(3))
        .isInstanceOf(cn.edu.upms.shared.BusinessException.class);
  }

  @Test
  @DisplayName("ACC-F-005 会话访问不续期，在八小时时刻立即失效")
  void absoluteExpiry() {
    var clock = new MovingClock();
    var sessions = new UnifiedSessions(clock);
    var user = sessions.open(20);
    clock.now = clock.now.plusSeconds(28799);
    assertThat(sessions.valid(user)).isTrue();
    clock.now = clock.now.plusSeconds(1);
    assertThat(sessions.valid(user)).isFalse();
    sessions.expire();
    assertThat(sessions.valid(user)).isFalse();
  }

  private static final class MovingClock extends Clock {
    private Instant now = Instant.parse("2026-10-01T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
