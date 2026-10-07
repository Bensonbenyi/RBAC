package cn.edu.upms.identity.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 复用重复的系统凭据校验；键含当前存储哈希，签发新凭据后旧结果自然不能命中. */
final class ClientSecretVerifier implements PasswordEncoder {
  private final PasswordEncoder delegate =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();
  private final Cache<String, Boolean> verified =
      Caffeine.newBuilder().maximumSize(256).expireAfterWrite(Duration.ofMinutes(2)).build();

  @Override
  public String encode(CharSequence raw) {
    return delegate.encode(raw);
  }

  @Override
  public boolean matches(CharSequence raw, String encoded) {
    if (raw == null || encoded == null) {
      return false;
    }
    String key = fingerprint(raw, encoded);
    if (Boolean.TRUE.equals(verified.getIfPresent(key))) {
      return true;
    }
    // 首次仍执行成熟框架的自适应哈希比较。只缓存成功，错误凭据从不进入缓存。
    if (!delegate.matches(raw, encoded)) {
      return false;
    }
    verified.put(key, true);
    return true;
  }

  private static String fingerprint(CharSequence raw, String encoded) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(encoded.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      return HexFormat.of()
          .formatHex(digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }
}
