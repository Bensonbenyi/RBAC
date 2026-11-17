package cn.edu.xmu.rbac.service.port;

import java.time.Instant;

/** 登录会话持久化端口. */
public interface SessionStore {
  /** Session 数据契约. */
  record Session(String id, Long userId, String systemKey, Instant expiresAt, Instant revokedAt) {}

  void save(Session session);

  Session find(String id);

  void revoke(String id, Instant when);
}
