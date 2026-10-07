package cn.edu.upms.application;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 将已认证的后台页面交给本平台同源静态前端，权限仍由服务端逐次校验. */
@Controller
public class FrontendController {
  /** 主页和后台共用 Vue 入口，不使用浏览器可读业务令牌. */
  @GetMapping({"/", "/admin"})
  public String index() {
    return "forward:/index.html";
  }
}
