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

/** 压测客户端完成标准授权码与 PKCE 兑换，并验证身份签名，不能用直取令牌替代登录. */
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

  static ChainBuilder login() {
    return exec(session ->
            session
                .set("nonce", UUID.randomUUID().toString())
                .set("state", UUID.randomUUID().toString()))
        .exec(
            http("开始标准授权")
                .get("/oauth2/authorize")
                .queryParam("client_id", "oa")
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", "http://localhost:18081/login/oauth2/code/upms")
                .queryParam("code_challenge", CHALLENGE)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "#{state}")
                .queryParam("nonce", "#{nonce}")
                .disableFollowRedirect()
                .check(status().is(302)))
        .exec(
            http("取得登录页面及 CSRF")
                .get("/login")
                .check(status().is(200), css("input[name='_csrf']", "value").saveAs("csrf")))
        .exec(
            http("用户名手机号匹配")
                .post("/login")
                .formParam("username", "#{username}")
                .formParam("phone", "#{phone}")
                .formParam("_csrf", "#{csrf}")
                .disableFollowRedirect()
                .check(status().is(302), header("Location").saveAs("resume")))
        .exitHereIfFailed()
        .exec(
            http("取得授权码")
                .get("#{resume}")
                .disableFollowRedirect()
                .check(
                    status().is(302),
                    headerRegex("Location", "[?&]code=([^&]+)").saveAs("code"),
                    headerRegex("Location", "[?&]state=([^&]+)")
                        .is(session -> session.getString("state"))))
        .exitHereIfFailed()
        .exec(
            http("后端兑换授权码")
                .post("/oauth2/token")
                .basicAuth("oa", "oa-demo-secret")
                .header("Accept", "application/json")
                .formParam("grant_type", "authorization_code")
                .formParam("code", "#{code}")
                .formParam("code_verifier", VERIFIER)
                .formParam("redirect_uri", "http://localhost:18081/login/oauth2/code/upms")
                .check(
                    status().is(200),
                    jsonPath("$.access_token").saveAs("token"),
                    jsonPath("$.id_token").saveAs("idToken"),
                    jsonPath("$.refresh_token").notExists()))
        .exitHereIfFailed()
        .exec(
            session -> {
              try {
                SignedJWT token = SignedJWT.parse(session.getString("idToken"));
                var claims = token.getJWTClaimsSet();
                boolean valid =
                    JWSAlgorithm.RS256.equals(token.getHeader().getAlgorithm())
                        && token.verify(new RSASSAVerifier(SIGNING_KEY))
                        && BASE.equals(claims.getIssuer())
                        && claims.getAudience().contains("oa")
                        && session.getString("nonce").equals(claims.getStringClaim("nonce"))
                        && Instant.now().isBefore(claims.getExpirationTime().toInstant());
                return valid ? session : session.markAsFailed();
              } catch (Exception invalid) {
                return session.markAsFailed();
              }
            })
        .exitHereIfFailed()
        .exec(
            http("验证身份端点")
                .get("/userinfo")
                .header("Authorization", "Bearer #{token}")
                .header("Accept", "application/json")
                .check(
                    status().is(200),
                    jsonPath("$.sub").is(session -> session.getString("employeeId"))));
  }

  static ChainBuilder checkPermission() {
    return exec(http("实时鉴权")
            .post("/api/runtime/check")
            .header("Authorization", "Bearer #{token}")
            .asJson()
            .body(StringBody("{\"system\":\"oa\",\"permission\":\"f001:查看\"}"))
            .check(status().is(200), jsonPath("$.allowed").ofBoolean().saveAs("actualAllowed")))
        .exec(
            session -> {
              if (session.isFailed()) {
                return session;
              }
              boolean expected = Boolean.parseBoolean(session.getString("expectedAllowed"));
              if (session.getBoolean("actualAllowed") != expected) {
                WRONG_DECISIONS.increment();
                return session.markAsFailed();
              }
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
