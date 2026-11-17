package cn.edu.xmu.rbac.api.identity;

import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** IdentityUser 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
public class IdentityUserController {
  private final AuthService service;
  private final CurrentIdentity current;

  public IdentityUserController(AuthService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/identity/users/{id}")
  public AuthService.IdentityUser get(@PathVariable @Positive Long id) {
    return service.lookup(current.get(), id);
  }
}
