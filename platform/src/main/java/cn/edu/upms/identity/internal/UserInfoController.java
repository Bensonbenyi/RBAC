package cn.edu.upms.identity.internal;

import cn.edu.upms.organization.api.OrganizationApi;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 标准 OIDC UserInfo 资源适配，仅返回身份资料，令牌和范围由安全过滤链核验. */
@RestController
public class UserInfoController {
  private final OrganizationApi organization;

  /** 注入组织身份事实，不接触员工实体. */
  public UserInfoController(OrganizationApi organization) {
    this.organization = organization;
  }

  /** sub 与 ID Token 完全一致，业务权限不放入身份结果. */
  @GetMapping("/userinfo")
  public Map<String, Object> userInfo(
      @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
    var employee = organization.employee(Long.parseLong(principal.getName()));
    return Map.of(
        "sub",
        principal.getName(),
        "name",
        employee.name(),
        "preferred_username",
        employee.username());
  }
}
