package cn.edu.xmu.rbac.api.auth;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.port.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {
  private final AuthService service;
  private final CurrentIdentity current;
  private final UserUseCase users;

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
    return Map.of("allowed", service.allowed(current.get(), permission));
  }

  @GetMapping("/api-permissions/{operationId}")
  public Map<String, Boolean> api(@PathVariable @NotBlank @Size(max = 100) String operationId) {
    var actor = current.get();
    String permission = OperationPermissions.code(actor.systemKey(), operationId);
    return Map.of("allowed", permission != null && service.allowed(actor, permission));
  }
}
