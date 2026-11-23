package cn.edu.xmu.rbac.service.catalog;

import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.PermissionUseCase;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission 目录服务，由两种策略共用. */
@Service
@Transactional
public class PermissionService implements PermissionUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  public PermissionService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  public Data.Page<Data.Permission> list(Data.Identity actor, Long systemId, int page, int size) {
    access.require(actor, "permission.manage");
    return store.permissions(systemId, page, size);
  }
}
