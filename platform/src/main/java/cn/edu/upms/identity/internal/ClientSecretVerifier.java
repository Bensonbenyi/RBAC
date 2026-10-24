package cn.edu.upms.identity.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 缓存系统客户端凭据的成功比较，降低频繁认证中的自适应哈希开销.
 *
 * <p>首次始终委托框架编码器验证；缓存键是"当前数据库哈希与输入凭据"的摘要，不保存明文。轮换使存储哈希改变，因此旧成功结果不能命中新组合。失败不缓存，缓存过期后重新校验。
 * 这是客户端身份验证优化，与员工权限快照及其三秒生效期限分别管理.
 */
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
