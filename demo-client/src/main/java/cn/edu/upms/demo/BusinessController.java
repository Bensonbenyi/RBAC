package cn.edu.upms.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 两个独立业务进程共享演示代码，以各自客户端身份逐次请求平台鉴权. */
@Controller
public class BusinessController {
  /** 业务数据只存在模拟系统，平台不存储内容或替它筛选本人记录. */
  private record Document(long id, String owner, String title, Instant createdAt) {}

  private record Decision(boolean allowed, String reason) {}

  @Value("${demo.oa-url:http://localhost:18081}")
  private String oaUrl = "http://localhost:18081";

  @Value("${demo.knowledge-url:http://localhost:18082}")
  private String knowledgeUrl = "http://localhost:18082";

  private final String platformUrl;
  private final String system;
  private final String title;
  private final String readPermission;
  private final String writePermission;
  private final String ownPermission;
  private final RestClient platform;
  private final CopyOnWriteArrayList<Document> documents = new CopyOnWriteArrayList<>();
  private final AtomicLong ids = new AtomicLong();

  /** 配置有限连接与响应期限；超时、断线或非成功响应均拒绝执行业务操作. */
  public BusinessController(
      @Value("${demo.system}") String system,
      @Value("${demo.title}") String title,
      @Value("${demo.permission-read}") String readPermission,
      @Value("${demo.permission-write}") String writePermission,
      @Value("${demo.permission-own}") String ownPermission,
      @Value("${demo.platform}") String url) {
    this.platformUrl = url;
    this.system = system;
    this.title = title;
    this.readPermission = readPermission;
    this.writePermission = writePermission;
    this.ownPermission = ownPermission;
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
    factory.setReadTimeout(Duration.ofSeconds(1));
    this.platform = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    documents.add(new Document(ids.incrementAndGet(), "13", "我的演示记录", Instant.now()));
    documents.add(new Document(ids.incrementAndGet(), "14", "另一员工的演示记录", Instant.now()));
  }

  /** 登录页只展示身份，每次受控操作都会另行实时判断权限. */
  @GetMapping("/")
  public String home(@AuthenticationPrincipal OidcUser user, Model model) {
    model.addAttribute("oaUrl", oaUrl);
    model.addAttribute("knowledgeUrl", knowledgeUrl);
    model.addAttribute("platformUrl", platformUrl);
    model.addAttribute("title", title);
    model.addAttribute("system", system);
    model.addAttribute("user", user);
    return "business";
  }

  /** 读取业务内容前核验权限，业务记录范围由当前模拟系统执行. */
  @PostMapping("/documents/read")
  public String read(
      @AuthenticationPrincipal OidcUser user,
      @RegisteredOAuth2AuthorizedClient("upms") OAuth2AuthorizedClient client,
      Model model) {
    if (!authorize(client, readPermission, model)) {
      return home(user, model);
    }
    boolean ownOnly = readPermission.contains("本人");
    model.addAttribute(
        "documents",
        documents.stream().filter(d -> !ownOnly || d.owner().equals(user.getSubject())).toList());
    model.addAttribute("message", ownOnly ? "已允许；业务系统只显示本人记录" : "已允许查看业务记录");
    return home(user, model);
  }

  /** 写入动作先实时鉴权，再由外部系统保存业务数据. */
  @PostMapping("/documents/write")
  public String write(
      @AuthenticationPrincipal OidcUser user,
      @RegisteredOAuth2AuthorizedClient("upms") OAuth2AuthorizedClient client,
      @RequestParam String name,
      Model model) {
    if (!authorize(client, writePermission, model)) {
      return home(user, model);
    }
    if (name == null || name.isBlank() || name.length() > 120) {
      model.addAttribute("message", "标题须为 1 至 120 字符");
      return home(user, model);
    }
    documents.add(
        new Document(ids.incrementAndGet(), user.getSubject(), name.trim(), Instant.now()));
    model.addAttribute("message", "已允许并保存业务记录");
    return home(user, model);
  }

  /** 本人查看示例同时检查独立权限和记录所有者，不让平台判断业务记录归属. */
  @PostMapping("/documents/own")
  public String own(
      @AuthenticationPrincipal OidcUser user,
      @RegisteredOAuth2AuthorizedClient("upms") OAuth2AuthorizedClient client,
      @RequestParam long id,
      Model model) {
    if (!authorize(client, ownPermission, model)) {
      return home(user, model);
    }
    boolean owned =
        documents.stream().anyMatch(d -> d.id() == id && d.owner().equals(user.getSubject()));
    model.addAttribute("message", owned ? "本人范围校验通过" : "业务系统拒绝：记录不属于本人");
    return home(user, model);
  }

  private boolean authorize(OAuth2AuthorizedClient client, String permission, Model model) {
    try {
      Decision result =
          platform
              .post()
              .uri("/api/runtime/check")
              .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
              .body(Map.of("system", system, "permission", permission))
              .retrieve()
              .body(Decision.class);
      if (result == null || !result.allowed()) {
        model.addAttribute("message", "权限拒绝：" + (result == null ? "无有效结果" : result.reason()));
        return false;
      }
      return true;
    } catch (RestClientException failure) {
      model.addAttribute("message", "操作拒绝：统一身份或鉴权服务不可用，请重新登录或稍后重试");
      return false;
    }
  }
}
