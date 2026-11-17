package cn.edu.xmu.rbac.service.auth;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private final ManagementStore store;
  private final SessionStore sessions;
  private final AccessPolicy access;
  private final JwtEncoder encoder;
  private final Duration lifetime;
  private final String issuer;

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

  public record LoginResult(String accessToken, String tokenType, long expiresIn) {}

  public record Me(
      String subject,
      String username,
      String name,
      String departmentId,
      List<String> roles,
      List<String> permissions) {}

  public record IdentityUser(String userId, String no, String name) {}

  @Transactional
  public LoginResult login(String username, String password, String systemKey) {
    Data.User user;
    try {
      user = store.userByUsername(username);
    } catch (BusinessException e) {
      throw unauthenticated();
    }
    if (!MessageDigest.isEqual(
        user.phone().getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8)))
      throw unauthenticated();
    if (!access.allowed(user.id(), systemKey, "login")) throw BusinessException.forbidden();
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now(), end = now.plus(lifetime);
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

  public Me me(Data.Identity actor) {
    var user = store.user(actor.userId());
    return new Me(
        user.id().toString(),
        user.username(),
        user.name(),
        user.departmentId().toString(),
        store.rolesForUser(user.id()),
        store.permissionsForUser(user.id(), actor.systemKey()));
  }

  @Transactional
  public void logout(Data.Identity actor) {
    sessions.revoke(actor.sessionId(), Instant.now());
    log.debug("logout user={} system={}", actor.userId(), actor.systemKey());
  }

  public boolean allowed(Data.Identity actor, String permission) {
    return access.allowed(actor.userId(), actor.systemKey(), permission);
  }

  public IdentityUser lookup(Data.Identity actor, Long id) {
    if (!Objects.equals(actor.userId(), id) && !allowed(actor, "UL"))
      throw BusinessException.forbidden();
    var user = store.user(id);
    return new IdentityUser(user.id().toString(), user.username(), user.name());
  }

  public static BusinessException unauthenticated() {
    return new BusinessException(401, "UNAUTHENTICATED", "凭据无效或登录已过期");
  }
}
