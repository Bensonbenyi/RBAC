package cn.edu.xmu.rbac.service.port;

import java.time.Instant;

public interface SessionStore {
  record Session(String id, Long userId, String systemKey, Instant expiresAt, Instant revokedAt) {}

  void save(Session session);

  Session find(String id);

  void revoke(String id, Instant when);
}
