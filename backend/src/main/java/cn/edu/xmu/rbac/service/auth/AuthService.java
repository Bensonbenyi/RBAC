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
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
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
  public record Me(
      String subject,
      String username,
      String name,
      String departmentId,
      List<String> roles,
      List<String> permissions) {}

  /** IdentityUser 数据契约. */
  public record IdentityUser(String userId, String no, String name) {}

  /** 验证账号和目标系统登录许可并签发身份凭据. */
  @Transactional
  public LoginResult login(String username, String password, String systemKey) {
    Data.User user;
    try {
      user = store.userByUsername(username);
    } catch (BusinessException e) {
      throw unauthenticated();
    }
    if (!MessageDigest.isEqual(
        user.phone().getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8))) {
      throw unauthenticated();
    }
    if (!access.allowed(user.id(), systemKey, "login")) {
      throw BusinessException.forbidden();
    }
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant end = now.plus(lifetime);
    sessions.save(new SessionStore.Session(id, user.id(), systemKey, end, null));
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.id().toString())
            .audience(List.of(systemKey))
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
    log.debug("login user={} system={}", user.id(), systemKey);
    return new LoginResult(token, "Bearer", lifetime.toSeconds());
  }

  /** 读取本人资料及当前系统界面权限. */
  public Me me(Data.Identity actor) {
    var user = store.user(actor.userId());
    return new Me(
        user.id().toString(),
        user.username(),
        user.name(),
        user.departmentId().toString(),
        store.rolesForUser(user.id()),
        store.permissionsForUser(user.id(), actor.systemKey()).stream()
            .filter(code -> UiPermissions.contains(actor.systemKey(), code))
            .toList());
  }

  @Transactional
  public void logout(Data.Identity actor) {
    sessions.revoke(actor.sessionId(), Instant.now());
    log.debug("logout user={} system={}", actor.userId(), actor.systemKey());
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
    return code != null && allowed(actor, code);
  }

  public boolean allowed(Data.Identity actor, String permission) {
    return access.allowed(actor.userId(), actor.systemKey(), permission);
  }

  /** lookup 的用例处理. */
  public IdentityUser lookup(Data.Identity actor, Long id) {
    if (!Objects.equals(actor.userId(), id) && !allowed(actor, "UL")) {
      throw BusinessException.forbidden();
    }
    var user = store.user(id);
    return new IdentityUser(user.id().toString(), user.username(), user.name());
  }

  public static BusinessException unauthenticated() {
    return new BusinessException(401, "UNAUTHENTICATED", "凭据无效或登录已过期");
  }
}
