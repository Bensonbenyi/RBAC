package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredUserService implements UserUseCase {
  private static final Logger log = LoggerFactory.getLogger(StructuredUserService.class);
  private final ManagementStore store;
  private final AccessPolicy access;

  public StructuredUserService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  private Data.User scoped(Data.Identity actor, Long id) {
    var user = store.user(id);
    access.scope(actor, user.departmentId());
    return user;
  }

  public Data.Page<Data.User> list(Data.Identity actor, int page, int size) {
    access.require(actor, "user.view");
    access.scope(actor, actor.departmentId());
    return store.users(actor.departmentId(), page, size);
  }

  public Data.User get(Data.Identity actor, Long id) {
    access.require(actor, "user.view");
    return scoped(actor, id);
  }

  public Data.User create(Data.Identity actor, Data.CreateUser command) {
    access.require(actor, "user.create");
    access.scope(actor, actor.departmentId());
    log.debug("createUser actor={}", actor.userId());
    return store.createUser(actor.departmentId(), command);
  }

  public void update(Data.Identity actor, Long id, Data.Profile command) {
    access.require(actor, "user.edit");
    scoped(actor, id);
    var old = store.user(id);
    store.updateUser(
        id,
        new Data.Profile(
            command.name() == null ? old.name() : command.name(),
            command.phone() == null ? old.phone() : command.phone()));
    log.debug("updateUser actor={} target={}", actor.userId(), id);
  }

  public void updateMe(Data.Identity actor, Data.Profile command) {
    Long id = actor.userId();
    var old = store.user(id);
    store.updateUser(
        id,
        new Data.Profile(
            command.name() == null ? old.name() : command.name(),
            command.phone() == null ? old.phone() : command.phone()));
    log.debug("updateMe actor={}", id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "user.delete");
    scoped(actor, id);
    store.deleteUser(id);
    log.debug("deleteUser actor={} target={}", actor.userId(), id);
  }

  public List<Data.Role> roles(Data.Identity actor, Long id) {
    access.require(actor, "user.role.assign");
    scoped(actor, id);
    return store.userRoles(id);
  }

  public void assign(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    var user = scoped(actor, userId);
    var role = store.role(roleId);
    access.scope(actor, role.departmentId());
    if (store.hasRole(userId, roleId)) throw BusinessException.conflict();
    store.assignRole(userId, roleId);
    log.debug("assignRole actor={} user={} role={}", actor.userId(), userId, roleId);
  }

  public void revoke(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    scoped(actor, userId);
    access.scope(actor, store.role(roleId).departmentId());
    store.revokeRole(userId, roleId);
    log.debug("revokeRole actor={} user={} role={}", actor.userId(), userId, roleId);
  }
}
