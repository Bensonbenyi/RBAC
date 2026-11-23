package cn.edu.xmu.rbac.api.auth.vo;

import java.util.List;

/** 统一身份接口的响应字段. */
public final class IdentityVOs {
  /** 隐藏工具类构造入口. */
  private IdentityVOs() {}

  /**
   * 签发结果，有效期以秒为单位.
   *
   * @param accessToken 签发的访问令牌
   * @param tokenType 凭据类型 Bearer
   * @param expiresIn 以秒计的令牌有效时间
   */
  public record LoginVO(String accessToken, String tokenType, long expiresIn) {}

  /**
   * 当前身份、工号、角色与目标系统的界面权限.
   *
   * @param subject 登录账号的统一身份标识
   * @param no 统一身份目录中的工号或学号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   * @param roles 角色名称列表
   * @param permissions 目标系统有效的界面权限码列表
   */
  public record CurrentUserVO(
      String subject,
      String no,
      String name,
      Long departmentId,
      List<String> roles,
      List<String> permissions) {}

  /**
   * 查询身份目录时可公开的用户字段.
   *
   * @param userId 用户的唯一编号
   * @param no 统一身份目录中的工号或学号
   * @param name 用于展示的记录名称
   */
  public record UserVO(String userId, String no, String name) {}

  /**
   * 部门树的单层节点，hasChildren 表示可继续展开.
   *
   * @param id 目标记录的唯一编号
   * @param code 统一身份目录中的部门编码
   * @param name 用于展示的记录名称
   * @param parentId 父部门编号，根层使用 null
   * @param hasChildren 是否存在直接子部门
   */
  public record DepartmentVO(
      Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /**
   * 候选人身份及所属部门.
   *
   * @param userId 用户的唯一编号
   * @param no 统一身份目录中的工号或学号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   * @param departmentName 所属部门的显示名称
   */
  public record DepartmentUserVO(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /**
   * 从第一页开始的身份目录分页.
   *
   * @param <T> 共享数据项的类型
   * @param page 从 0 开始的页号
   * @param pageSize 每页最多返回的记录数
   * @param total 符合条件的记录总数
   * @param items 当前页记录
   */
  public record PageVO<T>(int page, int pageSize, long total, List<T> items) {}

  /**
   * 权限决策仅返回是否获准.
   *
   * @param allowed 是否拥有该项操作许可
   */
  public record AllowedVO(boolean allowed) {}

  /**
   * API 操作标识和对应的许可结果.
   *
   * @param operationId API 操作的唯一标识
   * @param allowed 是否拥有该项操作许可
   */
  public record ApiAuthorityVO(String operationId, boolean allowed) {}
}
