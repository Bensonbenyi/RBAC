package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 用户查询和角色分配用例契约。结构化和 DDD 共用本接口. */
public interface UserUseCase {
  /**
   * 分页查询用户.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.User> list(Data.Identity actor, int page, int size);

  /**
   * 查询已分配角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param id 用户编号
   * @return 满足查询条件的角色列表
   */
  List<Data.Role> roles(Data.Identity actor, Long id);

  /**
   * 为用户分配角色.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  void assign(Data.Identity actor, Long userId, Long roleId);

  /**
   * 撤销用户角色关联.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param userId 用户的唯一编号
   * @param roleId 角色的唯一编号
   */
  void revoke(Data.Identity actor, Long userId, Long roleId);
}
