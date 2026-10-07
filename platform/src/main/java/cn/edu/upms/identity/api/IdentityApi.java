package cn.edu.upms.identity.api;

import java.security.Principal;
import java.time.Instant;

/** 身份和统一会话能力；业务权限始终由授权模块实时判断. */
public interface IdentityApi {
  /** 已核验的员工会话，固定 expiresAt 不因访问延长. */
  record SessionIdentity(long employeeId, String sessionId, Instant expiresAt) {}

  /** 已核验的不透明令牌身份，绑定唯一接入系统. */
  record TokenIdentity(long employeeId, long systemId, String sessionId, Instant expiresAt) {}

  /** 核验后台浏览器身份和绝对期限；管理员资格由组织模块另行核验. */
  SessionIdentity browser(Principal principal);

  /** 核验员工令牌、系统归属、会话及员工状态，不读取业务权限. */
  TokenIdentity token(String token, String expectedSystem);

  /** 核验独立目录凭据换得的令牌和上报范围，返回登记系统编号. */
  long directory(String token);

  /** 员工停用事务提交后使其全部统一会话立即失效. */
  void revokeEmployeeSessions(long employeeId);

  /** 结束当前浏览器会话；审计失败也不恢复会话，返回是否可靠记账. */
  boolean logout(Principal principal);
}
