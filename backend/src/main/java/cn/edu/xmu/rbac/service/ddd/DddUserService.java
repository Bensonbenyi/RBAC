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

  public DddUserService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  private Data.User scoped(Data.Identity actor, Long id) {
    Data.User user = store.user(id);
    access.scope(actor, user.departmentId());
    return user;
  }

  /** 分页查询当前用例可见的对象. */
  public Data.Page<Data.User> list(Data.Identity actor, int page, int size) {
    access.require(actor, "user.view");
    return store.users(actor.departmentId(), page, size);
  }

  public Data.User get(Data.Identity actor, Long id) {
    access.require(actor, "user.view");
    return scoped(actor, id);
  }

  /** 校验输入后创建对象. */
  public Data.User create(Data.Identity actor, Data.CreateUser command) {
    access.require(actor, "user.create");
    return store.createUser(actor.departmentId(), command);
  }

  /** 修改白名单字段并保持归属不变. */
  public void update(Data.Identity actor, Long id, Data.Profile command) {
    access.require(actor, "user.edit");
    User user = new User(scoped(actor, id));
    user.changeProfile(command);
    store.updateUser(id, user.profile());
  }

  /** 修改当前用户的姓名和手机号. */
  public void updateMe(Data.Identity actor, Data.Profile command) {
    Long id = actor.userId();
    User user = new User(store.user(id));
    user.changeProfile(command);
    store.updateUser(id, user.profile());
  }

  /** 检查引用关系后删除对象. */
  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "user.delete");
    scoped(actor, id);
    store.deleteUser(id);
  }

  /** 查询用户已分配角色. */
  public List<Data.Role> roles(Data.Identity actor, Long id) {
    access.require(actor, "user.role.assign");
    scoped(actor, id);
    return store.userRoles(id);
  }

  /** 在本部门范围内分配角色. */
  public void assign(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    Data.User user = scoped(actor, userId);
    Data.Role role = store.role(roleId);
    new User(user, store.userRoles(userId)).assignRole(role, actor.departmentId());
    store.assignRole(userId, roleId);
  }

  /** 撤销指定授权关联. */
  public void revoke(Data.Identity actor, Long userId, Long roleId) {
    access.require(actor, "user.role.assign");
    scoped(actor, userId);
    access.scope(actor, store.role(roleId).departmentId());
    store.revokeRole(userId, roleId);
  }
}
