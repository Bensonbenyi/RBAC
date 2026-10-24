package cn.edu.upms.identity.internal;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;

/**
 * 装饰框架授权码 Provider，保证本单节点内同一授权码只能成功兑换一次.
 *
 * <p>仅使 Map 的 put 线程安全，不能阻止两个线程先后读到"未兑换"。锁必须覆盖框架整个读取、验证、消费和保存流程。固定 256 把条带锁控制内存，同码总落在同一把锁；哈希碰撞
 * 只增加等待。本方案不提供多个平台实例之间的兑换互斥.
 */
final class AtomicCodeExchange implements AuthenticationProvider {
  private final AuthenticationProvider delegate;
  private final ReentrantLock[] stripes = new ReentrantLock[256];

  AtomicCodeExchange(AuthenticationProvider delegate) {
    this.delegate = delegate;
    for (int i = 0; i < stripes.length; i++) {
      stripes[i] = new ReentrantLock();
    }
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    var exchange = (OAuth2AuthorizationCodeAuthenticationToken) authentication;
    ReentrantLock lock = stripes[Math.floorMod(exchange.getCode().hashCode(), stripes.length)];
    lock.lock();
    try {
      // 覆盖读取、验证、失效和保存全过程，避免两个线程均读到未兑换状态。
      return delegate.authenticate(authentication);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return delegate.supports(authentication);
  }
}
