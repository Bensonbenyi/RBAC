package cn.edu.xmu.rbac.service.ddd;

import cn.edu.xmu.rbac.service.ddd.domain.User;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.UserUseCase;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User 用例的 DDD 应用服务实现. */
@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddUserService implements UserUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  /**
   * 注入用户所需的协作组件.
   *
   * @param store 管理数据读写端口
   * @param access 检查目标许可和部门归属的组件
   */
  public DddUserService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /**
   * 读取并校验当前操作者可管理的用户.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 用户编号
   * @return 目标用户的数据
   */
  private Data.User scoped(Data.Identity actor, Long id) {
    Data.User user = store.user(id);
    access.scope(actor, user.departmentId());
    return user;
  }

  /**
   * 分页查询用户.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.User> list(Data.Identity actor, int page, int size) {
    access.require(actor, "user.view");
    return store.users(actor.departmentId(), page, size);
  }

  /**
   * 查询用户已分配角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 用户编号
   * @return 满足查询条件的角色列表
   */
  public List<Data.Role> roles(Data.Identity actor, Long id) {
    access.require(actor, "user.role.assign");
    scoped(actor, id);
    return store.userRoles(id);
  }

  /**
   * 在本部门范围内分配角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  public void assign(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    User user = new User(scoped(actor, userId));
    user.requireRoleInDepartment(store.role(roleId));
    store.assignRole(userId, roleId);
  }

  /**
   * 撤销指定授权关联.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  public void revoke(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    scoped(actor, userId);
    access.scope(actor, store.role(roleId).departmentId());
    store.revokeRole(userId, roleId);
  }
}
