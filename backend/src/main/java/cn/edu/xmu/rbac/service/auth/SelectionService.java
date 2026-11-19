package cn.edu.xmu.rbac.service.auth;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.stereotype.Service;

/** 授权表单的只读候选项。查询权限来自当前授权任务，不开放目录写权限. */
@Service
public class SelectionService {
  private final ManagementStore store;
  private final AccessPolicy access;

  public SelectionService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /** 查询本部门可分配的角色，供用户授权表单选择. */
  public Data.Page<Data.Role> roles(Data.Identity actor, int page, int size) {
    access.require(actor, "user.role.assign");
    return store.roles(actor.departmentId(), page, size);
  }

  /** 查询授权和权限目录表单可选的业务系统. */
  public Data.Page<Data.System> systems(Data.Identity actor, int page, int size) {
    if (!"rbac".equals(actor.systemKey())
        || !(access.allowed(actor.userId(), "rbac", "role.manage")
            || access.allowed(actor.userId(), "rbac", "permission.manage"))) {
      throw BusinessException.forbidden();
    }
    return store.systems(page, size);
  }

  /** 查询指定系统的权限候选项，供角色授权表单选择. */
  public Data.Page<Data.Permission> permissions(
      Data.Identity actor, Long systemId, int page, int size) {
    access.require(actor, "role.manage");
    return store.permissions(systemId, page, size);
  }
}
