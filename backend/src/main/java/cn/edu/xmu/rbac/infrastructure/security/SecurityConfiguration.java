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
  /**
   * 配置身份 CORS 预检和Bearer 令牌过滤器.
   *
   * @param http Spring Security 的 HTTP 配置
   * @param decoder 验证并解析 JWT 的组件
   * @param sessions 查询和撤销登录会话的端口
   * @param store 管理数据读写端口
   * @param effectiveAccess 查询角色继承与有效权限的端口
   * @param allowedOrigins 允许跨域访问统一身份接口的网页来源
   * @return 用于当前 HTTP 请求的 Spring Security 过滤链
   * @throws Exception 安全配置或密钥加载失败
   */
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
