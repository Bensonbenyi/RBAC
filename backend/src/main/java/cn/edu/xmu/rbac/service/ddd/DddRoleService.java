package cn.edu.xmu.rbac.service.ddd;

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
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleService implements RoleUseCase {
  private static final Logger log = LoggerFactory.getLogger(DddRoleService.class);
  private final ManagementStore store;
  private final AccessPolicy access;

  public DddRoleService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  private Data.Role scoped(Data.Identity actor, Long id) {
    var role = store.role(id);
    access.scope(actor, role.departmentId());
    return role;
  }

  public Data.Page<Data.Role> list(Data.Identity actor, int page, int size) {
    access.require(actor, "role.manage");
    access.scope(actor, actor.departmentId());
    return store.roles(actor.departmentId(), page, size);
  }

  public Data.Role get(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    return scoped(actor, id);
  }

  public Data.Role create(Data.Identity actor, Data.Name command) {
    access.require(actor, "role.manage");
    access.scope(actor, actor.departmentId());
    log.debug("createRole actor={}", actor.userId());
    return store.createRole(actor.departmentId(), command.name());
  }

  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    var role = new cn.edu.xmu.rbac.service.ddd.domain.Role(store.role(id));
    role.rename(command.name());
    store.renameRole(id, role.name());
    log.debug("renameRole actor={} target={}", actor.userId(), id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.deleteRole(id);
    log.debug("deleteRole actor={} target={}", actor.userId(), id);
  }

  public List<Data.Permission> permissions(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return store.rolePermissions(id);
  }

  public void grant(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    new cn.edu.xmu.rbac.service.ddd.domain.Role(store.role(id), store.rolePermissions(id))
        .grantPermission(store.permission(permissionId));
    store.grantPermission(id, permissionId);
    log.debug("grantPermission actor={} role={} permission={}", actor.userId(), id, permissionId);
  }

  public void revoke(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.permission(permissionId);
    var aggregate =
        new cn.edu.xmu.rbac.service.ddd.domain.Role(store.role(id), store.rolePermissions(id));
    aggregate.revokePermission(permissionId);
    store.revokePermission(id, permissionId);
    log.debug("revokePermission actor={} role={} permission={}", actor.userId(), id, permissionId);
  }
}
