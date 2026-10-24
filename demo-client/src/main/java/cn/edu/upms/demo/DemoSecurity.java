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

/**
 * 使用框架标准 OIDC 客户端完成跳转、授权码兑换和身份结果验证.
 *
 * <p>框架保存发起请求的 state、nonce 及 PKCE 校验材料，回调时校验关联关系与 ID Token 签名。登录完成只建立本系统会话，业务权限仍由
 * BusinessController 逐次远程检查。退出先清理本地会话，再由标准处理器带 ID Token hint 发起平台统一退出.
 */
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
