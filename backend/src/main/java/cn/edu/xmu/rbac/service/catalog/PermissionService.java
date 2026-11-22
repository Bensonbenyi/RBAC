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

  /** 校验输入后创建对象. */
  public Data.Permission create(Data.Identity actor, Long systemId, Data.CreatePermission command) {
    access.require(actor, "permission.manage");
    store.system(systemId);
    return store.createPermission(systemId, command);
  }

  /** 修改白名单字段并保持归属不变. */
  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "permission.manage");
    store.permission(id);
    store.renamePermission(id, command.name());
  }

  /** 检查引用关系后删除对象. */
  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "permission.manage");
    store.permission(id);
    store.deletePermission(id);
  }
}
