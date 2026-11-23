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

  /**
   * 注入当前模块所需的协作组件.
   *
   * @param sessions 登录会话 JPA 仓储
   */
  public JpaSessionStore(LoginSessionJpaRepository sessions) {
    this.sessions = sessions;
  }

  /**
   * 保存签发令牌对应的登录会话.
   *
   * @param session 包含令牌标识、账号和有效期的会话记录
   */
  @Override
  public void save(Session session) {
    sessions.save(new LoginSessionEntity(session));
  }

  /**
   * 按令牌标识查找会话.
   *
   * @param id 目标记录的唯一编号
   * @return 已保存的会话，找不到时返回 null
   */
  @Override
  public Session find(String id) {
    return sessions.findById(id).map(LoginSessionEntity::toSession).orElse(null);
  }

  /**
   * 标记会话撤销时间.
   *
   * @param id 目标记录的唯一编号
   * @param when 记录撤销时使用的时间
   */
  @Override
  @Transactional
  public void revoke(String id, Instant when) {
    sessions.revoke(id, when);
  }
}
