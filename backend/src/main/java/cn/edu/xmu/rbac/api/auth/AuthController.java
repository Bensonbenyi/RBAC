package cn.edu.xmu.rbac.api.auth;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.UserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Auth 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {
  private final AuthService service;
  private final CurrentIdentity current;
  private final UserUseCase users;

  /** 装配AuthController所需依赖. */
  public AuthController(AuthService service, CurrentIdentity current, UserUseCase users) {
    this.service = service;
    this.current = current;
    this.users = users;
  }

  @PostMapping("/login")
  public AuthService.LoginResult login(@RequestBody @Valid Requests.Login request) {
    return service.login(request.username(), request.password(), request.systems().getFirst());
  }

  @GetMapping("/me")
  public AuthService.Me me() {
    return service.me(current.get());
  }

  @PatchMapping("/me")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void updateMe(@RequestBody @Valid Requests.Profile request) {
    users.updateMe(current.get(), CloneFactory.copy(new Data.Profile(null, null), request));
  }

  @PostMapping("/logout")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void logout() {
    service.logout(current.get());
  }

  @GetMapping("/permissions/{permission}")
  public Map<String, Boolean> check(@PathVariable @NotBlank @Size(max = 100) String permission) {
    return Map.of("allowed", service.uiAllowed(current.get(), permission));
  }

  @GetMapping("/api-permissions/{operationId}")
  public Map<String, Boolean> api(@PathVariable @NotBlank @Size(max = 100) String operationId) {
    return Map.of("allowed", service.apiAllowed(current.get(), operationId));
  }
}
