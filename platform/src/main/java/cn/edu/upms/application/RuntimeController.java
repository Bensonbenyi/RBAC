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

/** 协调令牌身份与业务判断，两者分别由身份模块和授权模块负责. */
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
    return authorization.decide(user.employeeId(), user.systemId(), request.permission());
  }

  static String bearer(String header) {
    if (header == null || !header.startsWith("Bearer ") || header.length() > 4096) {
      throw new BusinessException(401, "INVALID_BEARER", "缺少有效访问令牌");
    }
    return header.substring(7);
  }
}
