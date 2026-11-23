package cn.edu.xmu.rbac.service.port;

import java.time.Instant;

/** 登录会话持久化端口. */
public interface SessionStore {
  /**
   * 记录访问令牌的用户、有效期和撤销状态.
   *
   * @param id JWT 对应的会话标识
   * @param userId 用户的唯一编号
   * @param expiresAt 会话失效时间
   * @param revokedAt 会话撤销时间，null 表示尚未撤销
   */
  record Session(String id, Long userId, Instant expiresAt, Instant revokedAt) {}

  /**
   * 保存签发令牌对应的登录会话.
   *
   * @param session 包含令牌标识、账号和有效期的会话记录
   */
  void save(Session session);

  /**
   * 按令牌标识查找会话.
   *
   * @param id JWT 对应的会话标识
   * @return 已保存的会话，找不到时返回 null
   */
  Session find(String id);

  /**
   * 标记会话撤销时间.
   *
   * @param id JWT 对应的会话标识
   * @param when 记录撤销时使用的时间
   */
  void revoke(String id, Instant when);
}
