package cn.edu.upms.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

/** 独立模拟系统使用标准 OIDC 客户端；令牌仅存在后端，不交给浏览器脚本. */
@Configuration
public class DemoSecurity {
  /** 框架校验 state、nonce、签名及发行者，同时显式启用保密客户端 PKCE. */
  @Bean
  public SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository clients)
      throws Exception {
    var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    var logout = new OidcClientInitiatedLogoutSuccessHandler(clients);
    logout.setPostLogoutRedirectUri("{baseUrl}");
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/", "/error", "/favicon.ico")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            login ->
                login
                    .authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(resolver))
                    .defaultSuccessUrl("/", true))
        .logout(config -> config.logoutSuccessHandler(logout));
    return http.build();
  }
}
