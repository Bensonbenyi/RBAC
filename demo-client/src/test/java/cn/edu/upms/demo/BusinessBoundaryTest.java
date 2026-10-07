package cn.edu.upms.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ExtendedModelMap;

/** 模拟系统通过真实 HTTP 调用边界，检查故障时不写入业务数据及本人范围由本系统执行. */
class BusinessBoundaryTest {
  @Test
  @DisplayName("ACC-F-016 断开鉴权服务时拒绝写入，恢复后重新判断")
  void unavailableDoesNotWrite() throws Exception {
    var server = server(200, "{\"allowed\":false,\"reason\":\"NO_ROLE\"}", 0);
    int port = server.getAddress().getPort();
    server.stop(0);
    var controller = controller(port);
    var model = new ExtendedModelMap();
    controller.write(user(), client(), "不应保存", model);
    assertThat(model.get("message").toString()).contains("操作拒绝");
    var restored = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    restored.createContext(
        "/api/runtime/check",
        request -> {
          byte[] body = "{\"allowed\":true,\"reason\":\"ALLOW\"}".getBytes(StandardCharsets.UTF_8);
          request.getResponseHeaders().set("Content-Type", "application/json");
          request.sendResponseHeaders(200, body.length);
          request.getResponseBody().write(body);
          request.close();
        });
    restored.start();
    try {
      controller.read(user(), client(), model);
      assertThat((List<?>) model.get("documents")).hasSize(2);
    } finally {
      restored.stop(0);
    }
  }

  @Test
  @DisplayName("ACC-F-016 鉴权超时与 HTTP 异常均拒绝，不把技术故障转换为允许")
  void timeoutAndFailure() throws Exception {
    for (int response : List.of(200, 503)) {
      var server =
          server(response, "{\"allowed\":true,\"reason\":\"ALLOW\"}", response == 200 ? 1500 : 0);
      try {
        var model = new ExtendedModelMap();
        controller(server.getAddress().getPort()).write(user(), client(), "不应保存", model);
        assertThat(model.get("message").toString()).contains("操作拒绝");
      } finally {
        server.stop(0);
      }
    }
  }

  @Test
  @DisplayName("ACC-F-012 平台只判断本人动作权限，模拟系统自行拒绝其他员工记录")
  void businessOwnership() throws Exception {
    var server = server(200, "{\"allowed\":true,\"reason\":\"ALLOW\"}", 0);
    try {
      var controller = controller(server.getAddress().getPort());
      var model = new ExtendedModelMap();
      controller.own(user(), client(), 2, model);
      assertThat(model.get("message")).isEqualTo("业务系统拒绝：记录不属于本人");
      controller.own(user(), client(), 1, model);
      assertThat(model.get("message")).isEqualTo("本人范围校验通过");
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer server(int status, String json, long delay) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/runtime/check",
        request -> {
          try {
            if (delay > 0) {
              Thread.sleep(delay);
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(status, body.length);
            request.getResponseBody().write(body);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } finally {
            request.close();
          }
        });
    server.start();
    return server;
  }

  private static BusinessController controller(int port) {
    return new BusinessController(
        "oa", "测试模拟系统", "f001:查看", "f002:新增", "demo-own:查看本人", "http://127.0.0.1:" + port);
  }

  private static OidcUser user() {
    var user = mock(OidcUser.class);
    when(user.getSubject()).thenReturn("13");
    return user;
  }

  private static OAuth2AuthorizedClient client() {
    var registration =
        ClientRegistration.withRegistrationId("upms")
            .clientId("oa")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .authorizationUri("http://localhost/authorize")
            .tokenUri("http://localhost/token")
            .build();
    var token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Set.of("openid"));
    return new OAuth2AuthorizedClient(registration, "13", token);
  }
}
