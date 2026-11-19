package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.UserUseCase;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User 用例的结构化事务脚本实现. */
@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredUserService implements UserUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  public StructuredUserService(ManagementStore store, AccessPolicy access) {
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
    Data.User old = scoped(actor, id);
    store.updateUser(
        id, CloneFactory.copyNotNull(new Data.Profile(old.name(), old.phone()), command));
  }

  /** 修改当前用户的姓名和手机号. */
  public void updateMe(Data.Identity actor, Data.Profile command) {
    Long id = actor.userId();
    Data.User old = store.user(id);
    store.updateUser(
        id, CloneFactory.copyNotNull(new Data.Profile(old.name(), old.phone()), command));
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
    access.scope(actor, role.departmentId());
    if (store.hasRole(userId, roleId)) {
      throw BusinessException.conflict();
    }
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
