package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 角色与授权用例契约。结构化和 DDD 共用本接口. */
public interface RoleUseCase {
  /**
   * 分页查询角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Role> list(Data.Identity actor, int page, int size);

  /**
   * 创建当前部门的角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param command 包含角色名称的用例输入
   * @return 目标角色的数据
   */
  Data.Role create(Data.Identity actor, Data.Name command);

  /**
   * 修改角色名称.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param command 包含角色名称的用例输入
   */
  void update(Data.Identity actor, Long id, Data.Name command);

  /**
   * 删除角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   */
  void delete(Data.Identity actor, Long id);

  /**
   * 查询角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @return 满足查询条件的权限列表
   */
  List<Data.Permission> permissions(Data.Identity actor, Long id);

  /**
   * 为角色授予直接权限.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param permissionId 权限的唯一编号
   */
  void grant(Data.Identity actor, Long id, Long permissionId);

  /**
   * 撤销角色授权.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @param permissionId 权限的唯一编号
   */
  void revoke(Data.Identity actor, Long id, Long permissionId);

  /**
   * 查询直接继承的基础角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @return 满足查询条件的角色列表
   */
  List<Data.Role> inheritedRoles(Data.Identity actor, Long id);

  /**
   * 查询直接与继承权限的去重并集.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 角色编号
   * @return 满足查询条件的权限列表
   */
  List<Data.Permission> effectivePermissions(Data.Identity actor, Long id);

  /**
   * 新增高级角色到基础角色的继承关系.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @param juniorId 基础角色的唯一编号
   */
  void inherit(Data.Identity actor, Long id, Long juniorId);

  /**
   * 解除指定角色继承关系.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 高级角色的编号
   * @param juniorId 基础角色的唯一编号
   */
  void uninherit(Data.Identity actor, Long id, Long juniorId);
}
