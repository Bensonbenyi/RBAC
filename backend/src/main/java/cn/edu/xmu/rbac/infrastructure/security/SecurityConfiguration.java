package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SessionStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Spring Security 由 BearerFilter 建立每个请求的身份. */
@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder decoder,
      SessionStore sessions,
      ManagementStore store,
      EffectiveAccessStore effectiveAccess,
      @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins)
      throws Exception {
    // 接入网页发送 Bearer 令牌，预检由 Spring 处理，业务请求继续经过鉴权。
    CorsConfiguration identityCors = new CorsConfiguration();
    identityCors.setAllowedOrigins(allowedOrigins);
    identityCors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    identityCors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Target-System"));
    UrlBasedCorsConfigurationSource cors = new UrlBasedCorsConfigurationSource();
    cors.registerCorsConfiguration("/auth/**", identityCors);
    return http.cors(config -> config.configurationSource(cors))
        .csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(c -> c.anyRequest().permitAll())
        .addFilterBefore(
            new BearerFilter(decoder, sessions, store, effectiveAccess),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
