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
    // 使用框架解析客户端登记与授权地址，保留 state、nonce 关联和回调验证。
    var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
    // 即使后端客户端有密钥，也显式启用 PKCE 来绑定发起与兑换请求。
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    // 标准退出处理器携带当前 ID Token hint 发起平台统一退出。
    var logout = new OidcClientInitiatedLogoutSuccessHandler(clients);
    // 退出回到当前应用基址，该地址须已在平台登记。
    logout.setPostLogoutRedirectUri("{baseUrl}");
    // 首页与健康检查可公开访问，实际业务操作要求本地已登录。
    http.authorizeHttpRequests(
            requests ->
                requests
                    // 只开放明确列出的页面及静态图标，避免扩大匿名可访问范围。
                    .requestMatchers("/", "/error", "/favicon.ico")
                    .permitAll()
                    // 健康检查只允许匿名 GET，其他方法仍沿用后续认证要求。
                    .requestMatchers(HttpMethod.GET, "/health")
                    .permitAll()
                    // 其余请求必须有本系统认证；业务方法仍须再次实时鉴权。
                    .anyRequest()
                    .authenticated())
        // 由框架完成授权码兑换及 ID Token 校验，不让浏览器直接持有后端令牌。
        .oauth2Login(
            login ->
                login
                    // 把启用 PKCE 的 resolver 接入标准登录链。
                    .authorizationEndpoint(
                        endpoint -> endpoint.authorizationRequestResolver(resolver))
                    // 登录完成统一返回首页，业务权限在点击受控操作时再判断。
                    .defaultSuccessUrl("/", true))
        // 本地退出后继续标准 RP 退出；当前链保留框架默认 CSRF 防护。
        .logout(config -> config.logoutSuccessHandler(logout));
    // 构建本模拟系统独立的过滤链和会话边界，不访问平台数据库。
    return http.build();
  }
}
