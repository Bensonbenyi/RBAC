package cn.edu.xmu.rbac.infrastructure.persistence.adapter;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.LoginSessionEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.repository.LoginSessionJpaRepository;
import cn.edu.xmu.rbac.service.port.SessionStore;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 登录会话通过 Spring Data JPA 保存和撤销. */
@Repository
public class JpaSessionStore implements SessionStore {
  private final LoginSessionJpaRepository sessions;

  public JpaSessionStore(LoginSessionJpaRepository sessions) {
    this.sessions = sessions;
  }

  @Override
  public void save(Session session) {
    sessions.save(new LoginSessionEntity(session));
  }

  @Override
  public Session find(String id) {
    return sessions.findById(id).map(LoginSessionEntity::toSession).orElse(null);
  }

  @Override
  @Transactional
  public void revoke(String id, Instant when) {
    sessions.revoke(id, when);
  }
}
