package cn.edu.upms.identity.api;

import java.security.Principal;
import java.time.Instant;

/**
 * 将浏览器身份、员工访问令牌与目录令牌转换成可信调用事实.
 *
 * <p>HttpSession Cookie 只供浏览器在本应用内建立身份，Bearer 访问令牌供业务系统后端调用。 ID Token 用于陈述 OIDC
 * 登录身份，不能拿它代替业务访问令牌或当前权限判断。校验失败抛出异常；业务无权的普通拒绝由授权模块返回.
 */
public interface IdentityApi {
  /** 已核验的员工会话，固定 expiresAt 不因访问延长. */
  record SessionIdentity(long employeeId, String sessionId, Instant expiresAt) {}

  /**
   * 通过验证的员工访问令牌上下文.
   *
   * @param employeeId 登录员工编号，来源于平台保存的认证结果
   * @param systemId 令牌绑定的系统编号，防止 OA 令牌冒充知识库
   * @param sessionId 此令牌依赖的统一会话编号，统一退出后失效
   * @param expiresAt 访问令牌到期时间，不晚于统一会话绝对期限
   */
  record TokenIdentity(long employeeId, long systemId, String sessionId, Instant expiresAt) {}

  /** 核验后台浏览器身份和绝对期限；管理员资格由组织模块另行核验. */
  SessionIdentity browser(Principal principal);

  /**
   * 验证访问令牌本体、授权类型、目标系统、统一会话和员工当前状态.
   *
   * @param token 从请求 Authorization 头提取的不透明访问令牌，不能写日志
   * @param expectedSystem 本次请求声明的系统标识，必须与令牌绑定系统一致
   * @return 供授权模块使用的可信员工与系统身份；不包含允许结论
   */
  TokenIdentity token(String token, String expectedSystem);

  /** 核验独立目录凭据换得的令牌和上报范围，返回登记系统编号. */
  long directory(String token);

  /** 员工停用事务提交后使其全部统一会话立即失效. */
  void revokeEmployeeSessions(long employeeId);

  /**
   * 结束 principal 对应的一次统一会话，并尝试同步记录退出事件.
   *
   * <p>同一员工其他浏览器的会话继续独立存在。退出优先执行，即使审计写入失败也不能让会话恢复。调用方仍须完成本应用 HttpSession 清理，并在失败时向用户说明审计缺口.
   *
   * @param principal 框架提供的认证身份；无对应统一身份时按无需退出处理
   * @return {@code true} 表示无需退出或退出审计成功；{@code false} 表示会话已撤销但审计失败
   */
  boolean logout(Principal principal);
}
