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

/** 配置标准 OIDC、独立目录凭据与同源后台会话；协议校验采用成熟安全框架. */
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

  /** 启动节点的签名密钥；课程单节点重启后统一会话失效. */
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

  /** Bearer 受控端点不使用浏览器会话；具体系统与范围由身份用例校验. */
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
