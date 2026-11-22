package cn.edu.xmu.rbac.infrastructure.persistence.entity;

import cn.edu.xmu.rbac.service.port.SessionStore.Session;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 技术登录会话的持久化映射. */
@Entity
@Table(name = "login_session")
public class LoginSessionEntity {
  @Id String id;
  Long userId;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  Instant expiresAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  Instant revokedAt;

  protected LoginSessionEntity() {}

  /** 构造待插入的技术登录会话记录. */
  public LoginSessionEntity(Session session) {
    id = session.id();
    userId = session.userId();
    expiresAt = session.expiresAt();
  }

  public Session toSession() {
    return new Session(id, userId, expiresAt, revokedAt);
  }
}
