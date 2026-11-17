package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.port.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder decoder, SessionStore sessions, ManagementStore store)
      throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(c -> c.anyRequest().permitAll())
        .addFilterBefore(
            new BearerFilter(decoder, sessions, store), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
