package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.SessionStore.Session;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "login_session")
class LoginSessionEntity {
  @Id String id;
  Long userId;
  String systemKey;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  Instant expiresAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  Instant revokedAt;

  protected LoginSessionEntity() {}

  LoginSessionEntity(Session session) {
    id = session.id();
    userId = session.userId();
    systemKey = session.systemKey();
    expiresAt = session.expiresAt();
  }

  Session toSession() {
    return new Session(id, userId, systemKey, expiresAt, revokedAt);
  }
}
