package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.shared.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务系统后端的实时鉴权入口，串接身份验证与当前权限判断.
 *
 * <p>先验证 Authorization 头得到可信 employeeId 和 systemId，再把它们交给授权模块。
 * 请求体不接收员工编号，防止调用方伪造另一个员工身份。登录成功、令牌有效和业务允许是不同结论；平台只返回判断，真正的业务操作仍由外部系统执行.
 */
@RestController
public class RuntimeController {
  /** 请求仅指定登记系统和稳定权限标识；员工身份必须来自令牌. */
  public record Check(
      @NotBlank @Size(max = 60) String system, @NotBlank @Size(max = 160) String permission) {}

  private final IdentityApi identity;
  private final AuthorizationApi authorization;
  private final AuditApi audit;

  /** 注入公开能力，控制器不接触持久化实体. */
  public RuntimeController(IdentityApi identity, AuthorizationApi authorization, AuditApi audit) {
    this.identity = identity;
    this.authorization = authorization;
    this.audit = audit;
  }

  /** 每次受控操作核验会话、系统与当前权限，技术异常不会转换为允许. */
  @PostMapping("/api/runtime/check")
  public AuthorizationApi.Decision check(
      @RequestHeader("Authorization") String header, @Valid @RequestBody Check request) {
    IdentityApi.TokenIdentity user;
    try {
      user = identity.token(bearer(header), request.system());
    } catch (BusinessException failure) {
      audit.count("ERROR", 0);
      audit.record(
          new AuditApi.Event(
              null,
              null,
              null,
              null,
              "IDENTITY_REJECTED",
              "runtime-check",
              "DENY",
              failure.getCode()));
      throw failure;
    }
    // 用令牌核验后的系统编号调用，不能直接信任请求体给出的目标归属。
    // 返回 allowed=false 是正常业务拒绝；身份无效或技术故障由异常响应表达。
    return authorization.decide(user.employeeId(), user.systemId(), request.permission());
  }

  static String bearer(String header) {
    if (header == null || !header.startsWith("Bearer ") || header.length() > 4096) {
      throw new BusinessException(401, "INVALID_BEARER", "缺少有效访问令牌");
    }
    return header.substring(7);
  }
}
