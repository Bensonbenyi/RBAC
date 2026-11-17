package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredPermissionService implements PermissionUseCase {
  private static final Logger log = LoggerFactory.getLogger(StructuredPermissionService.class);
  private final ManagementStore store;
  private final AccessPolicy access;

  public StructuredPermissionService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  public Data.Page<Data.Permission> list(Data.Identity actor, Long systemId, int page, int size) {
    access.require(actor, "permission.manage");
    return store.permissions(systemId, page, size);
  }

  public Data.Permission create(Data.Identity actor, Long systemId, Data.CreatePermission command) {
    access.require(actor, "permission.manage");
    store.system(systemId);
    log.debug("createPermission actor={}", actor.userId());
    return store.createPermission(systemId, command);
  }

  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "permission.manage");
    store.permission(id);
    store.renamePermission(id, command.name());
    log.debug("renamePermission actor={} target={}", actor.userId(), id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "permission.manage");
    store.permission(id);
    store.deletePermission(id);
    log.debug("deletePermission actor={} target={}", actor.userId(), id);
  }
}
