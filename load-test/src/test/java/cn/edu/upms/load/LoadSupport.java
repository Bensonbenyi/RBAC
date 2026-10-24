package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.css;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.headerRegex;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gatling 场景共用的完整登录链与权限正确性检查，运行前须启动专用压测平台.
 *
 * <p>Gatling Session 是一个虚拟用户的状态容器，与平台统一会话和 Servlet HttpSession 不同。每个账号各自保存
 * state、nonce、授权码和访问令牌；共享连接池只复用 TCP 连接，不共享身份。本地固定 verifier 是压测夹具，真实客户端应为每次授权随机生成 PKCE 材料.
 *
 * <p>技术请求成功不代表权限结论正确。checkPermission 另外比较 expectedAllowed， verifyDecisions
 * 要求错误允许与错误拒绝都为零，不用百分之一技术失败预算容忍越权.
 */
final class LoadSupport {
  static final String BASE = System.getProperty("load.base", "http://localhost:18080");
  static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789-ABCDEFG";
  static final Path ROOT = projectRoot();
  static final String CHALLENGE = challenge();
  static final HttpProtocolBuilder PROTOCOL =
      http.baseUrl(BASE)
          .acceptHeader("*/*")
          .maxConnectionsPerHost(500)
          .shareConnections()
          .disableWarmUp();
  private static final RSAPublicKey SIGNING_KEY = signingKey();
  static final LongAdder WRONG_DECISIONS = new LongAdder();

  private LoadSupport() {}

  private static Path projectRoot() {
    String configured = System.getProperty("load.root");
    if (configured != null) {
      return Path.of(configured).toAbsolutePath();
    }
    Path path = Path.of("").toAbsolutePath();
    while (path != null) {
      if (java.nio.file.Files.exists(path.resolve("platform/pom.xml"))) {
        return path;
      }
      path = path.getParent();
    }
    throw new IllegalStateException("请从项目目录运行，或显式指定 load.root");
  }

  /** 按真实协议顺序执行跳转、表单、回调关联、兑换、ID Token 验证与 UserInfo. */
  static ChainBuilder login() {
    // 先为每个虚拟用户单独生成 nonce 与 state，保存到 Gatling 状态容器。
    return exec(session ->
            session
                // nonce 关联 ID Token，state 关联授权回调；两者用途不同。
                .set("nonce", UUID.randomUUID().toString())
                .set("state", UUID.randomUUID().toString()))
        .exec(
            // 从标准授权入口开始，完整模拟一次后端应用发起的 OIDC 登录。
            http("开始标准授权")
                .get("/oauth2/authorize")
                // 公开客户端标识为 OA，response_type=code 要求返回授权码。
                .queryParam("client_id", "oa")
                .queryParam("response_type", "code")
                // 声明身份协议范围，并提供已登记的精确回调。
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", "http://localhost:18081/login/oauth2/code/upms")
                // 使用 S256 摘要绑定后续 verifier；固定 verifier 仅用于本地压测夹具。
                .queryParam("code_challenge", CHALLENGE)
                .queryParam("code_challenge_method", "S256")
                // 引用当前虚拟用户自己的值，禁止在并发用户之间共享登录关联材料。
                .queryParam("state", "#{state}")
                .queryParam("nonce", "#{nonce}")
                // 停在授权跳转响应处检查 302，后续显式读取统一登录页面。
                .disableFollowRedirect()
                .check(status().is(302)))
        .exec(
            // 取得真实表单 CSRF 值，Cookie 由该虚拟用户的 HTTP 会话保存。
            http("取得登录页面及 CSRF")
                .get("/login")
                .check(status().is(200), css("input[name='_csrf']", "value").saveAs("csrf")))
        .exec(
            // 提交 feeder 中当前账号资料，执行课程简化身份校验。
            http("用户名手机号匹配")
                .post("/login")
                // 用户名与手机号同时提交，缺少任一匹配都不能建立平台身份。
                .formParam("username", "#{username}")
                .formParam("phone", "#{phone}")
                // 附带页面获取的 CSRF，不绕过浏览器登录的防伪校验。
                .formParam("_csrf", "#{csrf}")
                .disableFollowRedirect()
                // 保存登录后的恢复地址，下一步继续最初授权请求。
                .check(status().is(302), header("Location").saveAs("resume")))
        // 当前阶段失败即停止该用户链，避免缺少状态继续产生连锁无效请求。
        .exitHereIfFailed()
        .exec(
            // 沿恢复地址继续授权，显式检查回调中的授权码和 state。
            http("取得授权码")
                .get("#{resume}")
                .disableFollowRedirect()
                .check(
                    status().is(302),
                    // 从回调提取本次码；state 必须等于此用户最初发送的值。
                    headerRegex("Location", "[?&]code=([^&]+)").saveAs("code"),
                    headerRegex("Location", "[?&]state=([^&]+)")
                        .is(session -> session.getString("state"))))
        // 回调关联失败不能继续兑换，避免把协议错误掩盖成成功登录。
        .exitHereIfFailed()
        .exec(
            // 模拟业务系统后端用客户端密钥和 PKCE verifier 兑换，浏览器不执行此步骤。
            http("后端兑换授权码")
                .post("/oauth2/token")
                // 密钥仅是隔离压测环境的固定夹具，响应按 JSON 解析。
                .basicAuth("oa", "oa-demo-secret")
                .header("Accept", "application/json")
                // 明确使用授权码模式，并提交刚从当前用户回调取得的码。
                .formParam("grant_type", "authorization_code")
                .formParam("code", "#{code}")
                // 提交原始 verifier 与同一个回调地址，由授权服务器验证请求绑定。
                .formParam("code_verifier", VERIFIER)
                .formParam("redirect_uri", "http://localhost:18081/login/oauth2/code/upms")
                .check(
                    status().is(200),
                    // 分别保存访问令牌和 ID Token，前者用于实时鉴权，后者用于身份验证。
                    jsonPath("$.access_token").saveAs("token"),
                    jsonPath("$.id_token").saveAs("idToken"),
                    // 本期不发行刷新令牌，响应出现该字段也应使验收失败。
                    jsonPath("$.refresh_token").notExists()))
        // 兑换失败时没有可信令牌，立即结束当前用户链。
        .exitHereIfFailed()
        .exec(
            session -> {
              try {
                // 解析 ID Token 后仍须验证签名与声明，能解析不代表可信。
                SignedJWT token = SignedJWT.parse(session.getString("idToken"));
                var claims = token.getJWTClaimsSet();
                // 依次要求 RS256、可信公钥签名及与目标平台一致的发行者。
                boolean valid =
                    JWSAlgorithm.RS256.equals(token.getHeader().getAlgorithm())
                        && token.verify(new RSASSAVerifier(SIGNING_KEY))
                        && BASE.equals(claims.getIssuer())
                        // 受众须为 OA，nonce 须匹配当前用户，阻止挪用其他客户端或登录结果。
                        && claims.getAudience().contains("oa")
                        && session.getString("nonce").equals(claims.getStringClaim("nonce"))
                        // 有效期也须通过；浏览器已有 Cookie 不代表此身份令牌永久有效。
                        && Instant.now().isBefore(claims.getExpirationTime().toInstant());
                // 任一校验未通过就标记当前 Gatling 用户失败。
                return valid ? session : session.markAsFailed();
              } catch (Exception invalid) {
                // 解析异常同样失败，不能因为 HTTP 200 就把完整登录记作成功。
                return session.markAsFailed();
              }
            })
        // 签名或声明校验失败不能继续使用令牌调用身份端点。
        .exitHereIfFailed()
        .exec(
            // 最后用访问令牌读取 UserInfo，验证服务端还认可该员工身份。
            http("验证身份端点")
                .get("/userinfo")
                // 发送后端获得的 Bearer，不能用 ID Token 替代访问令牌。
                .header("Authorization", "Bearer #{token}")
                .header("Accept", "application/json")
                .check(
                    status().is(200),
                    // UserInfo 的 subject 必须等于 feeder 中员工编号，防止账号交叉。
                    jsonPath("$.sub").is(session -> session.getString("employeeId"))));
  }

  static ChainBuilder checkPermission() {
    // 每次只发一个受控权限查询，便于按到达率施加持续鉴权负载。
    return exec(http("实时鉴权")
            .post("/api/runtime/check")
            // 读取该虚拟用户的后端令牌，并声明 JSON 请求体。
            .header("Authorization", "Bearer #{token}")
            .asJson()
            // 固定检查 OA 的查看动作，预期结果来自夹具，绝不作为请求输入。
            .body(StringBody("{\"system\":\"oa\",\"permission\":\"f001:查看\"}"))
            // 技术响应须成功，再单独读取允许结论；拒绝本身可以是正确业务结果。
            .check(status().is(200), jsonPath("$.allowed").ofBoolean().saveAs("actualAllowed")))
        .exec(
            session -> {
              // 请求技术失败时保留原失败，不再用不存在的结论做正确性比较。
              if (session.isFailed()) {
                return session;
              }
              // 从专用 CSV 读取预期值，它只供压测断言使用。
              boolean expected = Boolean.parseBoolean(session.getString("expectedAllowed"));
              // 错误允许和错误拒绝都计为判断错误，不占用可容忍技术失败预算。
              if (session.getBoolean("actualAllowed") != expected) {
                // 线程安全累计判断错误，并将当前用户链标为失败。
                WRONG_DECISIONS.increment();
                return session.markAsFailed();
              }
              // 实际值与预期一致时才保持成功状态。
              return session;
            });
  }

  static void verifyDecisions() {
    long wrong = WRONG_DECISIONS.sum();
    System.out.println("权限错误判断数=" + wrong);
    if (wrong != 0) {
      throw new IllegalStateException("权限判断错误零容忍，实际 " + wrong);
    }
  }

  private static String challenge() {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static RSAPublicKey signingKey() {
    try {
      var response =
          HttpClient.newBuilder()
              .connectTimeout(java.time.Duration.ofSeconds(2))
              .build()
              .send(
                  HttpRequest.newBuilder(URI.create(BASE + "/oauth2/jwks"))
                      .timeout(java.time.Duration.ofSeconds(10))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      return ((RSAKey) JWKSet.parse(response.body()).getKeys().getFirst()).toRSAPublicKey();
    } catch (Exception failure) {
      throw new IllegalStateException("无法读取签名密钥，请先启动压测平台", failure);
    }
  }
}
