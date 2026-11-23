package cn.edu.xmu.rbac.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** TokenConfiguration 数据契约. */
@Configuration
public class TokenConfiguration {
  /**
   * 载入 RSA 公钥.
   *
   * @param resource PEM 密钥资源
   * @return 从 PEM 解析的 RSA 公钥
   * @throws Exception 安全配置或密钥加载失败
   */
  @Bean
  RSAPublicKey publicKey(@Value("${app.jwt.public-key}") Resource resource) throws Exception {
    try (java.io.InputStream input = resource.getInputStream()) {
      return RsaKeyConverters.x509().convert(input);
    }
  }

  /**
   * 使用 RSA 公钥及签发方配置令牌校验器.
   *
   * @param key 标识关联记录的复合主键
   * @param issuer 受信任的令牌签发方标识
   * @return 按公钥和签发方校验的解码器
   */
  @Bean
  JwtDecoder jwtDecoder(RSAPublicKey key, @Value("${app.jwt.issuer}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  /**
   * 使用 RSA 密钥对配置令牌签发器.
   *
   * @param key 标识关联记录的复合主键
   * @param resource PEM 密钥资源
   * @return 使用 RSA 私钥签发令牌的编码器
   * @throws Exception 安全配置或密钥加载失败
   */
  @Bean
  JwtEncoder jwtEncoder(RSAPublicKey key, @Value("${app.jwt.private-key}") Resource resource)
      throws Exception {
    RSAPrivateKey privateKey;
    try (java.io.InputStream input = resource.getInputStream()) {
      privateKey = RsaKeyConverters.pkcs8().convert(input);
    }
    com.nimbusds.jose.jwk.RSAKey rsa =
        new com.nimbusds.jose.jwk.RSAKey.Builder(key)
            .privateKey(privateKey)
            .keyID("rbac-v3")
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
  }
}
