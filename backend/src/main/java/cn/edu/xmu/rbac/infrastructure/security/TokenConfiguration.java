package cn.edu.xmu.rbac.infrastructure.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class TokenConfiguration {
  @Bean
  RSAPublicKey publicKey(@Value("${app.jwt.public-key}") Resource resource) throws Exception {
    try (var input = resource.getInputStream()) {
      return RsaKeyConverters.x509().convert(input);
    }
  }

  @Bean
  JwtDecoder jwtDecoder(RSAPublicKey key, @Value("${app.jwt.issuer}") String issuer) {
    var decoder = NimbusJwtDecoder.withPublicKey(key).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(RSAPublicKey key, @Value("${app.jwt.private-key}") Resource resource)
      throws Exception {
    RSAPrivateKey privateKey;
    try (var input = resource.getInputStream()) {
      privateKey = RsaKeyConverters.pkcs8().convert(input);
    }
    var rsa =
        new com.nimbusds.jose.jwk.RSAKey.Builder(key)
            .privateKey(privateKey)
            .keyID("rbac-v3")
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
  }
}
