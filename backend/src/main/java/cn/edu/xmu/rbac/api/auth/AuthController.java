package cn.edu.xmu.rbac.api.auth;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.auth.dto.LoginDTO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.AllowedVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.CurrentUserVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.LoginVO;
import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.auth.AuthService.CurrentUserResult;
import cn.edu.xmu.rbac.service.auth.AuthService.LoginResult;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.UserUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 处理统一身份的登录、会话与权限请求. */
@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {
  private final AuthService service;
  private final CurrentIdentity current;
  private final UserUseCase users;

  /** 注入身份服务及个人资料用例. */
  public AuthController(AuthService service, CurrentIdentity current, UserUseCase users) {
    this.service = service;
    this.current = current;
    this.users = users;
  }

  /** 校验登录参数并返回签发结果. */
  @PostMapping("/login")
  public LoginVO login(@RequestBody @Valid LoginDTO request) {
    LoginResult result = service.login(request.username(), request.password(), request.systems());
    return new LoginVO(result.accessToken(), result.tokenType(), result.expiresIn());
  }

  /** 查询当前身份及目标系统的界面权限. */
  @GetMapping("/me")
  public CurrentUserVO me() {
    CurrentUserResult result = service.me(current.get());
    return new CurrentUserVO(
        result.subject(),
        result.no(),
        result.name(),
        result.departmentId(),
        result.roles(),
        result.permissions());
  }

  /** 保存当前用户允许修改的资料字段. */
  @PatchMapping("/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateMe(@RequestBody @Valid ManagementDTOs.ProfileDTO request) {
    users.updateMe(current.get(), CloneFactory.copy(new Data.Profile(null, null), request));
  }

  /** 撤销当前令牌对应的会话. */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    service.logout(current.get());
  }

  /** 查询一项界面权限. */
  @GetMapping("/permissions/{permission}")
  public AllowedVO check(@PathVariable @NotBlank String permission) {
    return new AllowedVO(service.uiAllowed(current.get(), permission));
  }

  /** 按 OpenAPI 的操作标识查询 API 权限. */
  @GetMapping("/api-permissions/{operationId}")
  public AllowedVO api(@PathVariable @NotBlank @Size(max = 128) String operationId) {
    return new AllowedVO(service.apiAllowed(current.get(), operationId));
  }
}
