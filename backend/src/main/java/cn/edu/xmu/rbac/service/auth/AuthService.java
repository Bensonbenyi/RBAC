package cn.edu.xmu.rbac.service.auth;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SessionStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认证会话、身份查询和独立权限决策. */
@Service
public class AuthService {
  private final ManagementStore store;
  private final SessionStore sessions;
  private final AccessPolicy access;
  private final JwtEncoder encoder;
  private final Duration lifetime;
  private final String issuer;

  /** 装配AuthService所需依赖. */
  public AuthService(
      ManagementStore store,
      SessionStore sessions,
      AccessPolicy access,
      JwtEncoder encoder,
      @Value("${app.jwt.lifetime}") Duration lifetime,
      @Value("${app.jwt.issuer}") String issuer) {
    this.store = store;
    this.sessions = sessions;
    this.access = access;
    this.encoder = encoder;
    this.lifetime = lifetime;
    this.issuer = issuer;
  }

  /** LoginResult 数据契约. */
  public record LoginResult(String accessToken, String tokenType, long expiresIn) {}

  /** Me 数据契约. */
  public record CurrentUserResult(
      String subject,
      String no,
      String name,
      Long departmentId,
      List<String> roles,
      List<String> permissions) {}

  /** 验证账号和目标系统登录许可并签发身份凭据. */
  @Transactional
  public LoginResult login(String username, String password, List<String> systems) {
    Data.User user;
    try {
      user = store.userByUsername(username);
    } catch (BusinessException e) {
      throw badCredentials();
    }
    if (!MessageDigest.isEqual(
        user.phone().getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8))) {
      throw badCredentials();
    }
    if (!systems.stream().allMatch(system -> access.allowed(user.id(), system, "login"))) {
      throw BusinessException.forbidden();
    }
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant end = now.plus(lifetime);
    sessions.save(new SessionStore.Session(id, user.id(), systems.getFirst(), end, null));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.id().toString())
            .audience(systems)
            .id(id)
            .issuedAt(now)
            .expiresAt(end)
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId("rbac-v3").build(), claims))
            .getTokenValue();
    return new LoginResult(token, "Bearer", lifetime.toSeconds());
  }

  /** 读取本人资料及当前系统界面权限. */
  public CurrentUserResult me(Data.Identity actor) {
    Data.User user = store.user(actor.userId());
    return new CurrentUserResult(
        user.id().toString(),
        user.username(),
        user.name(),
        user.departmentId(),
        roles(actor),
        store.permissionsForUser(user.id(), actor.systemKey()).stream()
            .filter(code -> UiPermissions.contains(actor.systemKey(), code))
            .toList());
  }

  /** 课程端使用契约规定的身份角色，兼容既有演示角色名. */
  private List<String> roles(Data.Identity actor) {
    List<String> names = store.rolesForUser(actor.userId());
    if (!"course-project-management".equals(actor.systemKey())) {
      return names;
    }
    return names.stream()
        .map(name -> name.equals("reviewer") || name.equals("viewer") ? "teacher" : name)
        .filter(List.of("admin", "teacher", "student")::contains)
        .distinct()
        .toList();
  }

  /** 撤销当前会话，所有目标系统共用同一令牌标识. */
  @Transactional
  public void logout(Data.Identity actor) {
    sessions.revoke(actor.sessionId(), Instant.now());
  }

  /** 界面检查只接受当前系统的界面码；API 码不会打开界面控件. */
  public boolean uiAllowed(Data.Identity actor, String permission) {
    return UiPermissions.contains(actor.systemKey(), permission) && allowed(actor, permission);
  }

  /** 授权入口在服务层；操作映射与界面权限各自独立. */
  public boolean apiAllowed(Data.Identity actor, String operationId) {
    if ("rbac".equals(actor.systemKey()) && "system.options".equals(operationId)) {
      return allowed(actor, "role.manage") || allowed(actor, "permission.manage");
    }
    String code = OperationPermissions.code(actor.systemKey(), operationId);
    if (code == null) {
      throw new BusinessException(404, "API_OPERATION_NOT_FOUND", "没有找到对应的 API 操作");
    }
    return allowed(actor, code);
  }

  private boolean allowed(Data.Identity actor, String permission) {
    return access.allowed(actor.userId(), actor.systemKey(), permission);
  }

  private static BusinessException badCredentials() {
    return new BusinessException(401, "UNAUTHORIZED", "账号或密码错误");
  }

  /** 无效或已撤销的令牌使用相同的身份错误. */
  public static BusinessException unauthenticated() {
    return new BusinessException(401, "UNAUTHORIZED", "凭据无效或登录已过期");
  }
}
