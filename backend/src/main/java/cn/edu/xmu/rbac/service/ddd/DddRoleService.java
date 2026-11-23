package cn.edu.xmu.rbac.service.ddd;

import cn.edu.xmu.rbac.service.ddd.domain.Role;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.RoleUseCase;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Role 用例的 DDD 应用服务实现. */
@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddRoleService implements RoleUseCase {
  private final ManagementStore store;
  private final EffectiveAccessStore effectiveAccess;
  private final AccessPolicy access;

  /**
   * 组装角色写入、有效权限查询和部门范围校验.
   *
   * @param store 管理数据读写端口
   * @param effectiveAccess 查询角色继承与有效权限的端口
   * @param access 检查目标许可和部门归属的组件
   */
  public DddRoleService(
      ManagementStore store, EffectiveAccessStore effectiveAccess, AccessPolicy access) {
    this.store = store;
    this.effectiveAccess = effectiveAccess;
    this.access = access;
  }

  /**
   * 读取并校验当前操作者可管理的角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @return 目标角色的数据
   */
  private Data.Role scoped(Data.Identity actor, Long id) {
    Data.Role role = store.role(id);
    access.scope(actor, role.departmentId());
    return role;
  }

  /**
   * 分页查询角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.Role> list(Data.Identity actor, int page, int size) {
    access.require(actor, "role.manage");
    return store.roles(actor.departmentId(), page, size);
  }

  /**
   * 校验输入后创建对象.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param command 包含角色名称的用例输入
   * @return 目标角色的数据
   */
  public Data.Role create(Data.Identity actor, Data.Name command) {
    access.require(actor, "role.manage");
    return store.createRole(actor.departmentId(), command.name());
  }

  /**
   * 修改白名单字段并保持归属不变.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param command 包含角色名称的用例输入
   */
  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.renameRole(id, command.name());
  }

  /**
   * 检查引用关系后删除对象.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   */
  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.deleteRole(id);
  }

  /**
   * 查询角色已授予权限.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @return 满足查询条件的权限列表
   */
  public List<Data.Permission> permissions(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return store.rolePermissions(id);
  }

  /**
   * 写入直接授权，组合主键拒绝重复记录.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param permissionId 权限的唯一编号
   */
  public void grant(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.permission(permissionId);
    store.grantPermission(id, permissionId);
  }

  /**
   * 撤销指定授权关联.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param permissionId 权限的唯一编号
   */
  public void revoke(Data.Identity actor, Long id, Long permissionId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    store.permission(permissionId);
    store.revokePermission(id, permissionId);
  }

  /**
   * 查询直接基础角色，归属与其他角色操作一致.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @return 满足查询条件的角色列表
   */
  public List<Data.Role> inheritedRoles(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return store.inheritedRoles(id);
  }

  /**
   * 权限并集包含所有可达基础角色，删除继承边后下次查询即生效.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @return 满足查询条件的权限列表
   */
  public List<Data.Permission> effectivePermissions(Data.Identity actor, Long id) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    return effectiveAccess.effectiveRolePermissions(id);
  }

  /**
   * 校验同部门并检查添加继承边后是否形成环.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @param juniorId 基础角色的唯一编号
   */
  public void inherit(Data.Identity actor, Long id, Long juniorId) {
    access.require(actor, "role.manage");
    var role = new Role(scoped(actor, id));
    var junior = store.role(juniorId);
    role.requireBaseRole(junior, effectiveAccess.reachableRoles(List.of(juniorId)));
    store.inheritRole(id, juniorId);
  }

  /**
   * 移除一条直接继承边；其他继承路径仍然有效，重复移除保持幂等.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @param juniorId 基础角色的唯一编号
   */
  public void uninherit(Data.Identity actor, Long id, Long juniorId) {
    access.require(actor, "role.manage");
    scoped(actor, id);
    scoped(actor, juniorId);
    store.removeInheritance(id, juniorId);
  }
}
