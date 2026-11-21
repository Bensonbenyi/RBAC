package cn.edu.xmu.rbac.service.ddd;

import cn.edu.xmu.rbac.service.ddd.domain.Role;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.RoleUseCase;
import cn.edu.xmu.rbac.service.role.RoleHierarchy;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Role 用例的 DDD 应用服务实现. */
@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleService implements RoleUseCase {
  private final ManagementStore store;
  private final EffectiveAccessStore effectiveAccess;
  private final AccessPolicy access;

  /** 组装角色写入、有效权限查询和部门范围校验. */
  public DddRoleService(
      ManagementStore store, EffectiveAccessStore effectiveAccess, AccessPolicy access) {
    this.store = store;
    this.effectiveAccess = effectiveAccess;
    this.access = access;
  }

  private Data.Role scoped(Data.Identity actor, Long id) {
    Data.Role role = store.role(id);
    access.scope(actor, role.departmentId());
    return role;
  }

  /** 分页查询当前用例可见的对象. */
  public Data.Page<Data.Role> list(Data.Identity actor, int page, int size) {
    access.require(actor, "role.manage");
    return store.roles(actor.departmentId(), page, size);
  }

  public Data.Role get(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    return scoped(actor, id);
  }

  /** 校验输入后创建对象. */
  public Data.Role create(Data.Identity actor, Data.Name command) {
    access.require(actor, "role.manage");
    return store.createRole(actor.departmentId(), command.name());
  }

  /** 修改白名单字段并保持归属不变. */
  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "role.manage");
    Role role = new Role(scoped(actor, id));
    role.rename(command.name());
    store.renameRole(id, role.name());
  }

  /** 检查引用关系后删除对象. */
  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.deleteRole(id);
  }

  /** 查询角色已授予权限. */
  public List<Data.Permission> permissions(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return store.rolePermissions(id);
  }

  /** 授予指定权限，重复授予保持幂等. */
  public void grant(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.permission(permissionId);
    store.grantPermission(id, permissionId);
  }

  /** 撤销指定授权关联. */
  public void revoke(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.permission(permissionId);
    store.revokePermission(id, permissionId);
  }

  /** 查询直接基础角色，归属与其他角色操作一致. */
  public List<Data.Role> inheritedRoles(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return store.inheritedRoles(id);
  }

  /** 权限并集包含所有可达基础角色，删除继承边后下次查询即生效. */
  public List<Data.Permission> effectivePermissions(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return effectiveAccess.effectiveRolePermissions(id);
  }

  /** 同部门串行写入，避免两次并发新增共同形成环. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void inherit(Data.Identity actor, Long id, Long juniorId) {
    access.require(actor, "role.manage");
    store.lockHierarchy(actor.departmentId());
    scoped(actor, id);
    scoped(actor, juniorId);
    List<Data.Inheritance> edges = store.inheritanceEdges(actor.departmentId());
    RoleHierarchy.requireAcyclic(edges, id, juniorId);
    store.inheritRole(id, juniorId);
  }

  /** 移除一条直接继承边；其他继承路径仍然有效，重复移除保持幂等. */
  public void uninherit(Data.Identity actor, Long id, Long juniorId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    scoped(actor, juniorId);
    store.removeInheritance(id, juniorId);
  }
}
