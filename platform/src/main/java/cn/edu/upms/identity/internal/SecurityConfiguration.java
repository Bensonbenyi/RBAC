package cn.edu.upms.identity.internal;

import cn.edu.upms.audit.api.AuditApi;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * 配置身份模块的安全入口，将标准协议交给 Spring Security 与授权服务器框架处理.
 *
 * <p>一次请求只进入首个匹配的 SecurityFilterChain，按 Order 从小到大选择：0 为 UserInfo 资源端点，1 为 OIDC/OAuth 协议端点，2 为
 * Bearer 业务调用，3 为浏览器与后台。分链后 Cookie 会话和 Bearer 令牌各有校验入口，不能因一处使用无状态策略就关闭后台 CSRF.
 *
 * <p>Bean 默认是单例，这里创建配置与线程安全协作对象；具体员工身份由每次请求的 SecurityContext 提供，不保存在配置类字段中。阅读时先看四条过滤链，再看令牌生成器.
 */
@Configuration
public class SecurityConfiguration {
  /** 提供可替换时钟，绝对会话期限的测试无需等待八小时. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /** OIDC 框架使用浏览器会话注册表生成及校验标准 sid. */
  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  /** 容器会话销毁时清理框架注册状态. */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  /**
   * 为当前进程创建 ID Token 签名密钥，并向框架提供 JWK 选择能力.
   *
   * <p>RSA 私钥只在内存中用于签名，公开 JWK 端点供客户端取得公钥验证。重启会换密钥，同时内存会话和授权存储清空；本期没有持久化密钥或跨节点共享密钥机制.
   *
   * @return 供框架签名及选择验证公钥的 JWK 来源
   * @throws Exception 当前运行环境不能创建 RSA 密钥时启动失败
   */
  @Bean
  public JWKSource<SecurityContext> jwkSource() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    RSAKey key =
        new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate())
            .keyID(UUID.randomUUID().toString())
            .build();
    JWKSet set = new JWKSet(key);
    return (selector, context) -> selector.select(set);
  }

  /** 框架校验 ID Token 签名与标准端点所需声明. */
  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) {
    return org.springframework.security.oauth2.server.authorization.config.annotation.web
        .configuration.OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
  }

  /** 按运行环境固定发行者，发现文档与模拟系统校验使用同一地址. */
  @Bean
  public AuthorizationServerSettings authorizationServerSettings(
      @Value("${upms.issuer}") String issuer) {
    return AuthorizationServerSettings.builder().issuer(issuer).build();
  }

  /** 不透明访问令牌的期限不能越过所属统一会话；ID Token 只陈述身份. */
  @Bean
  public OAuth2TokenGenerator<?> tokenGenerator(
      JWKSource<SecurityContext> source, IdentityService identity, AuditApi audit) {
    // 访问令牌采用 REFERENCE 格式，客户端不能解码后离线推断权限，需向平台实时验证。
    // 目录令牌没有员工 principal，沿用其独立五分钟寿命；员工令牌受固定八小时会话约束。
    OAuth2AccessTokenGenerator opaque = new OAuth2AccessTokenGenerator();
    opaque.setAccessTokenCustomizer(
        context -> {
          audit.requireAvailable();
          UnifiedPrincipal user = IdentityService.user(context.getPrincipal());
          if (user != null) {
            identity.requireUser(user);
            context.getClaims().expiresAt(user.expiresAt());
          }
        });
    // ID Token 仍按 OIDC 使用签名 JWT，作用是确认登录身份，不能当作业务授权快照。
    JwtGenerator jwt = new JwtGenerator(new NimbusJwtEncoder(source));
    jwt.setJwtCustomizer(
        context -> {
          UnifiedPrincipal user = IdentityService.user(context.getPrincipal());
          if (user != null
              && OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            identity.requireUser(user);
            if (context.getClaims().build().getExpiresAt().isAfter(user.expiresAt())) {
              context.getClaims().expiresAt(user.expiresAt());
            }
          }
        });
    return new DelegatingOAuth2TokenGenerator(jwt, opaque);
  }

  /** 授权端点优先于应用路由，校验 PKCE、客户端、回调、nonce 与授权码. */
  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServer(
      HttpSecurity http,
      IdentityService identity,
      OAuth2AuthorizationService authorizations,
      AuditApi audit)
      throws Exception {
    OAuth2AuthorizationServerConfigurer server =
        OAuth2AuthorizationServerConfigurer.authorizationServer();
    OidcLogoutAuthenticationSuccessHandler logout = new OidcLogoutAuthenticationSuccessHandler();
    var secretVerifier = new ClientSecretVerifier();
    logout.setLogoutHandler(new UnifiedOidcLogoutHandler(authorizations, identity));
    // matcher 决定本链负责哪些路径；authorizeHttpRequests 决定这些路径的访问要求。
    // 装饰框架 Provider 只补本项目约束，授权码、PKCE 和精确回调检查继续由框架完成。
    http.securityMatcher(server.getEndpointsMatcher())
        .with(
            server,
            config ->
                config
                    .clientAuthentication(
                        client ->
                            client.authenticationProviders(
                                providers -> {
                                  for (var provider : providers) {
                                    if (provider
                                        instanceof ClientSecretAuthenticationProvider secret) {
                                      secret.setPasswordEncoder(secretVerifier);
                                    }
                                  }
                                }))
                    .tokenIntrospectionEndpoint(
                        endpoint ->
                            endpoint.authenticationProviders(
                                providers -> {
                                  ProtocolProviders.liveIntrospection(providers, identity);
                                }))
                    .tokenEndpoint(
                        token ->
                            token.authenticationProviders(
                                providers -> {
                                  for (int i = 0; i < providers.size(); i++) {
                                    if (providers.get(i)
                                        instanceof OAuth2AuthorizationCodeAuthenticationProvider) {
                                      providers.set(i, new AtomicCodeExchange(providers.get(i)));
                                    }
                                  }
                                }))
                    .oidc(
                        oidc ->
                            oidc.logoutEndpoint(
                                endpoint ->
                                    endpoint.logoutResponseHandler(
                                        (request, response, authentication) -> {
                                          // 标准处理器先执行退出并校验过的重定向；故障标识随回调呈现。
                                          logout.onAuthenticationSuccess(
                                              request,
                                              new AuditGapResponse(response, request),
                                              authentication);
                                        }))))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .exceptionHandling(
            errors ->
                errors.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
        .addFilterAfter(new AbsoluteSessionFilter(identity), SecurityContextHolderFilter.class);
    return http.build();
  }

  /** UserInfo 是独立资源端点，使用框架不透明令牌验证，不混入 ID Token 的 JWT 配置. */
  @Bean
  @Order(0)
  public SecurityFilterChain userInfo(
      HttpSecurity http,
      IdentityService identity,
      OAuth2AuthorizationService authorizations,
      AuditApi audit)
      throws Exception {
    http.securityMatcher("/userinfo")
        .sessionManagement(
            session ->
                session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority("SCOPE_openid"))
        .oauth2ResourceServer(
            resource ->
                resource.opaqueToken(
                    token ->
                        token.introspector(
                            new UserInfoIntrospector(authorizations, identity, audit))));
    return http.build();
  }

  /**
   * 为运行鉴权与目录上报选择无状态的 Bearer 调用链.
   *
   * <p>这里的 permitAll 只让请求到达 Controller，绝不表示业务可匿名访问。RuntimeController 和 DirectoryController 必须调用
   * IdentityApi 核验 Authorization 头；增加同前缀新端点时也须保持该边界。本链不依赖浏览器自动附带的 Cookie，因而局部关闭 CSRF；后台链仍开启.
   */
  @Bean
  @Order(2)
  public SecurityFilterChain runtime(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/runtime/**", "/api/directory/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session ->
                session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
    return http.build();
  }

  /** 后台采用同源 HttpOnly 会话及 CSRF，每次管理用例重新校验管理员范围. */
  @Bean
  @Order(3)
  public SecurityFilterChain browser(
      HttpSecurity http,
      DemoAuthenticationProvider authentication,
      IdentityService identity,
      SessionRegistry sessions)
      throws Exception {
    http.authenticationProvider(authentication)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/login",
                        "/assets/**",
                        "/favicon.ico",
                        "/error",
                        "/api/csrf",
                        "/actuator/health",
                        "/actuator/health/readiness")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .usernameParameter("username")
                    // 复用表单过滤器的 credentials 槽位，Provider 实际匹配手机号，不执行密码验证。
                    .passwordParameter("phone")
                    .defaultSuccessUrl("/admin", false)
                    .failureHandler(
                        (request, response, failure) -> {
                          if (failure instanceof AuthenticationServiceException) {
                            response.setStatus(503);
                            response.setContentType("text/plain;charset=UTF-8");
                            response.getWriter().write("认证服务暂不可用，请稍后重试");
                          } else {
                            response.sendRedirect("/login?error");
                          }
                        }))
        .logout(
            logout ->
                logout
                    .addLogoutHandler(
                        (request, response, auth) -> {
                          if (!identity.logout(auth)) {
                            request.setAttribute("auditGap", true);
                          }
                        })
                    .logoutSuccessHandler(
                        (request, response, auth) ->
                            response.sendRedirect(
                                Boolean.TRUE.equals(request.getAttribute("auditGap"))
                                    ? "/login?audit=failed"
                                    : "/login?logout")))
        // -1 不限制员工同时登录几个浏览器；各统一会话仍有独立 sid 与固定绝对期限。
        .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions))
        .exceptionHandling(
            errors ->
                errors.defaultAuthenticationEntryPointFor(
                    (request, response, failure) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED),
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                        "/api/admin/**")))
        .csrf(Customizer.withDefaults())
        .addFilterAfter(
            new ManagementWriteGateFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
        .addFilterAfter(new AbsoluteSessionFilter(identity), SecurityContextHolderFilter.class);
    return http.build();
  }
}
