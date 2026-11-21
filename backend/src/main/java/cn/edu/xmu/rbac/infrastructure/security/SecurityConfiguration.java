package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SessionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Spring Security 由 BearerFilter 建立每个请求的身份. */
@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder decoder,
      SessionStore sessions,
      ManagementStore store,
      EffectiveAccessStore effectiveAccess)
      throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(c -> c.anyRequest().permitAll())
        .addFilterBefore(
            new BearerFilter(decoder, sessions, store, effectiveAccess),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
