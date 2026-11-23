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

  /**
   * 注入当前模块所需的协作组件.
   *
   * @param store 管理数据读写端口
   * @param access 检查目标许可和部门归属的组件
   */
  public SelectionService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /**
   * 查询当前部门可用于用户授权的角色候选项.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 按部门过滤的角色分页
   */
  public Data.Page<Data.Role> roles(Data.Identity actor, int page, int size) {
    access.require(actor, "user.role.assign");
    return store.roles(actor.departmentId(), page, size);
  }

  /**
   * 查询授权和权限目录表单可选的系统.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 系统候选项分页
   */
  public Data.Page<Data.System> systems(Data.Identity actor, int page, int size) {
    if (!"rbac".equals(actor.systemKey())
        || !(access.allowed(actor.userId(), "rbac", "role.manage")
            || access.allowed(actor.userId(), "rbac", "permission.manage"))) {
      throw BusinessException.forbidden();
    }
    return store.systems(page, size);
  }

  /**
   * 查询角色授权表单可选的系统权限.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 所选系统的权限候选项分页
   */
  public Data.Page<Data.Permission> permissions(
      Data.Identity actor, Long systemId, int page, int size) {
    access.require(actor, "role.manage");
    return store.permissions(systemId, page, size);
  }
}
