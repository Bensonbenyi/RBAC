package cn.edu.xmu.rbac.service.auth;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
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
  private final EffectiveAccessStore effectiveAccess;
  private final SessionStore sessions;
  private final AccessPolicy access;
  private final JwtEncoder encoder;
  private final Duration lifetime;
  private final String issuer;

  /**
   * 装配当前授权查询、会话记录及令牌签发.
   *
   * @param store 管理数据读写端口
   * @param effectiveAccess 查询角色继承与有效权限的端口
   * @param sessions 查询和撤销登录会话的端口
   * @param access 检查目标许可和部门归属的组件
   * @param encoder 签发 JWT 的组件
   * @param lifetime 访问令牌有效时长
   * @param issuer 受信任的令牌签发方标识
   */
  public AuthService(
      ManagementStore store,
      EffectiveAccessStore effectiveAccess,
      SessionStore sessions,
      AccessPolicy access,
      JwtEncoder encoder,
      @Value("${app.jwt.lifetime}") Duration lifetime,
      @Value("${app.jwt.issuer}") String issuer) {
    this.store = store;
    this.effectiveAccess = effectiveAccess;
    this.sessions = sessions;
    this.access = access;
    this.encoder = encoder;
    this.lifetime = lifetime;
    this.issuer = issuer;
  }

  /**
   * 成功响应包含令牌、凭据类型和秒级有效期.
   *
   * @param accessToken 签发的访问令牌
   * @param tokenType 凭据类型 Bearer
   * @param expiresIn 以秒计的令牌有效时间
   */
  public record LoginResult(String accessToken, String tokenType, long expiresIn) {}

  /**
   * 当前身份及目标系统可用的界面权限.
   *
   * @param subject 登录账号的统一身份标识
   * @param no 统一身份目录中的工号或学号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   * @param roles 角色名称列表
   * @param permissions 目标系统有效的界面权限码列表
   */
  public record CurrentUserResult(
      String subject,
      String no,
      String name,
      Long departmentId,
      List<String> roles,
      List<String> permissions) {}

  /**
   * 验证账号和目标系统登录许可并签发身份凭据.
   *
   * @param username 唯一登录账号
   * @param password 登录手机号
   * @param systems 拟访问的系统标识列表，首项是默认目标
   * @return 签发的 Bearer 令牌及有效期
   * @throws BusinessException 账号凭据无效或账号未获目标系统登录许可
   */
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
    sessions.save(new SessionStore.Session(id, user.id(), end, null));
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

  /**
   * 读取本人资料及当前系统界面权限.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @return 当前账号资料、角色及目标系统界面权限
   */
  public CurrentUserResult me(Data.Identity actor) {
    Data.User user = store.user(actor.userId());
    return new CurrentUserResult(
        user.id().toString(),
        user.username(),
        user.name(),
        user.departmentId(),
        roles(actor),
        effectiveAccess.permissionsForUser(user.id(), actor.systemKey()).stream()
            .filter(code -> UiPermissions.contains(actor.systemKey(), code))
            .toList());
  }

  /**
   * 按目标系统投影角色，课程系统只接收契约定义的身份角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @return 转换后的角色名称
   */
  private List<String> roles(Data.Identity actor) {
    List<String> names = effectiveAccess.rolesForUser(actor.userId());
    if (!"course-project-management".equals(actor.systemKey())) {
      return names;
    }
    return names.stream()
        .map(name -> name.equals("reviewer") || name.equals("viewer") ? "teacher" : name)
        .filter(List.of("admin", "teacher", "student")::contains)
        .distinct()
        .toList();
  }

  /**
   * 撤销当前会话，所有目标系统共用同一令牌标识.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   */
  @Transactional
  public void logout(Data.Identity actor) {
    sessions.revoke(actor.sessionId(), Instant.now());
  }

  /**
   * 界面检查只接受当前系统的界面码；API 码不会打开界面控件.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param permission 待检查的界面权限码
   * @return 符合目标系统的许可条件时为 true
   */
  public boolean uiAllowed(Data.Identity actor, String permission) {
    return UiPermissions.contains(actor.systemKey(), permission) && allowed(actor, permission);
  }

  /**
   * 授权入口在服务层；操作映射与界面权限各自独立.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param operationId API 操作的唯一标识
   * @return 符合目标系统的许可条件时为 true
   * @throws BusinessException 目标系统未登记指定操作
   */
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

  /**
   * 检查指定权限是否有效.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param permission 待检查的界面权限码
   * @return 符合目标系统的许可条件时为 true
   */
  private boolean allowed(Data.Identity actor, String permission) {
    return access.allowed(actor.userId(), actor.systemKey(), permission);
  }

  /**
   * 创建账号凭据验证错误.
   *
   * @return 对应业务失败原因的异常
   */
  private static BusinessException badCredentials() {
    return new BusinessException(401, "UNAUTHORIZED", "账号或密码错误");
  }

  /**
   * 无效或已撤销的令牌使用相同的身份错误.
   *
   * @return 对应业务失败原因的异常
   */
  public static BusinessException unauthenticated() {
    return new BusinessException(401, "UNAUTHORIZED", "凭据无效或登录已过期");
  }
}
