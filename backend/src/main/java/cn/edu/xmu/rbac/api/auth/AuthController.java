package cn.edu.xmu.rbac.api.auth;

import cn.edu.xmu.rbac.api.auth.dto.LoginDTO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.AllowedVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.ApiAuthorityVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.CurrentUserVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.LoginVO;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.auth.AuthService.CurrentUserResult;
import cn.edu.xmu.rbac.service.auth.AuthService.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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

  /**
   * 注入身份服务与当前身份上下文.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   */
  public AuthController(AuthService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /**
   * 提交登录凭据与目标系统并签发令牌.
   *
   * @param request 包含账号、手机号及目标系统的登录输入
   * @return 包含访问令牌和有效期的响应
   */
  @PostMapping("/login")
  public LoginVO login(@RequestBody @Valid LoginDTO request) {
    LoginResult result = service.login(request.username(), request.password(), request.systems());
    return new LoginVO(result.accessToken(), result.tokenType(), result.expiresIn());
  }

  /**
   * 查询当前账号在目标系统中的身份和权限.
   *
   * @return 当前身份、角色及界面权限
   */
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

  /** 撤销当前 Bearer 令牌对应的会话. */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    service.logout(current.get());
  }

  /**
   * 查询当前账号能否使用指定界面权限.
   *
   * @param permission 待检查的界面权限码
   * @return 界面许可结果
   */
  @GetMapping("/permissions/{permission}")
  public AllowedVO check(@PathVariable @NotBlank String permission) {
    return new AllowedVO(service.uiAllowed(current.get(), permission));
  }

  /**
   * 查询当前账号能否执行 API 操作.
   *
   * @param operationId API 操作的唯一标识
   * @return 操作标识及 API 许可结果
   */
  @GetMapping("/api-permissions/{operationId}")
  public ApiAuthorityVO api(@PathVariable @NotBlank @Size(max = 128) String operationId) {
    return new ApiAuthorityVO(operationId, service.apiAllowed(current.get(), operationId));
  }
}
