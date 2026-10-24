package cn.edu.upms.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

/** 使用真实安全过滤器验证匿名入口、CSRF 和标准 OIDC 跳转的保护边界. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoSecurityTest {
  @Autowired MockMvc mvc;

  @TestConfiguration
  static class Clients {
    @Bean
    ClientRegistrationRepository clients() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("upms")
              .clientId("oa")
              .clientSecret("test-only")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .authorizationUri("https://identity.example/authorize")
              .tokenUri("https://identity.example/token")
              .scope("openid")
              .jwkSetUri("https://identity.example/jwks")
              .build());
    }
  }

  @Test
  @DisplayName("ACC-F-003 模拟系统发起 OIDC 时携带 state、nonce 和 PKCE")
  void redirectIsProtected() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/health")).andExpect(status().isNotFound());
    mvc.perform(get("/documents/read")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/documents/write")).andExpect(status().isForbidden());
    var result =
        mvc.perform(get("/oauth2/authorization/upms"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    assertThat(result.getResponse().getRedirectedUrl())
        .contains("state=", "nonce=", "code_challenge=", "code_challenge_method=S256");
  }

  @Test
  @DisplayName("启动入口原样传递环境参数，实际上下文由本测试的 SpringBootTest 验证")
  void launchDelegatesArguments() {
    String[] arguments = {"--server.port=0"};
    try (var spring = mockStatic(SpringApplication.class)) {
      DemoApplication.main(arguments);
      spring.verify(() -> SpringApplication.run(DemoApplication.class, arguments));
    }
  }
}
