package cn.edu.xmu.rbac.infrastructure.security;

import cn.edu.xmu.rbac.service.port.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentIdentity {
  public Data.Identity get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Data.Identity identity))
      throw new BusinessException(401, "UNAUTHENTICATED", "请先登录");
    return identity;
  }
}
