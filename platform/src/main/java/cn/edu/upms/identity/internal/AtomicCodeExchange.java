package cn.edu.upms.identity.internal;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;

/** 在单节点内串行同一授权码兑换，验证和令牌生成仍由框架执行. */
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
