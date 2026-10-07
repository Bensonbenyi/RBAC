package cn.edu.upms.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 显示统一认证入口，协议继续请求和身份校验由安全框架完成. */
@Controller
public class LoginController {
  /** 统一登录页面，同一入口供后台及外部系统跳转使用. */
  @GetMapping("/login")
  public String login() {
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
