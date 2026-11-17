package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.SessionStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionStore implements SessionStore {
  private final JdbcTemplate jdbc;

  public JdbcSessionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(Session s) {
    jdbc.update(
        "INSERT INTO login_session(id,user_id,system_key,expires_at) VALUES(?,?,?,?)",
        s.id(),
        s.userId(),
        s.systemKey(),
        Timestamp.from(s.expiresAt()));
  }

  public Session find(String id) {
    var rows =
        jdbc.query(
            "SELECT * FROM login_session WHERE id=?",
            (r, n) ->
                new Session(
                    r.getString("id"),
                    r.getLong("user_id"),
                    r.getString("system_key"),
                    r.getTimestamp("expires_at").toInstant(),
                    r.getTimestamp("revoked_at") == null
                        ? null
                        : r.getTimestamp("revoked_at").toInstant()),
            id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void revoke(String id, Instant when) {
    jdbc.update(
        "UPDATE login_session SET revoked_at=? WHERE id=? AND revoked_at IS NULL",
        Timestamp.from(when),
        id);
  }
}
