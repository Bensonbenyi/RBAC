package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从当前安全上下文读取可信身份. */
@Component
public class CurrentIdentity {
  /**
   * 读取当前安全上下文中已认证的账号.
   *
   * @return 绑定到当前请求线程的身份
   * @throws BusinessException 当前上下文缺少已认证的账号身份
   */
  public Data.Identity get() {
    org.springframework.security.core.Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Data.Identity identity)) {
      throw new BusinessException(401, "UNAUTHORIZED", "请先登录");
    }
    return identity;
  }
}
