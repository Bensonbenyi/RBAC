package cn.edu.upms.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 提供登录页面与同源前端的 CSRF 令牌，不直接实现认证提交.
 *
 * <p>GET /login 返回模板；POST /login 由安全过滤链处理，并调用 DemoAuthenticationProvider。 CSRF
 * 用于证明浏览器写请求来自持有本应用会话的页面，不能代替员工身份或部门范围校验。认证成功后前端应重新取得 CSRF 资料，不能复用登录前可能已轮换的令牌.
 */
@Controller
public class LoginController {
  @Value("${upms.demo-oa-url:http://localhost:18081}")
  private String oaUrl = "http://localhost:18081";

  @Value("${upms.demo-knowledge-url:http://localhost:18082}")
  private String knowledgeUrl = "http://localhost:18082";

  /** 统一登录页面，同一入口供后台及外部系统跳转使用. */
  @GetMapping("/login")
  public String login(Model model) {
    model.addAttribute("oaUrl", oaUrl);
    model.addAttribute("knowledgeUrl", knowledgeUrl);
    return "login";
  }

  /** 给本机同源前端提供 CSRF 资料，不返回或操作员工权限. */
  @GetMapping("/api/csrf")
  @ResponseBody
  public Map<String, String> csrf(HttpServletRequest request) {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    return Map.of(
        "token",
        token.getToken(),
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName());
  }
}
