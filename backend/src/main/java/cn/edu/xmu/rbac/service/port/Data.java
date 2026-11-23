package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 双实现共享的不可变数据契约. */
public final class Data {
  /** 隐藏工具类构造入口. */
  private Data() {}

  /**
   * 用户账号及其部门归属.
   *
   * @param id 目标记录的唯一编号
   * @param username 唯一登录账号
   * @param name 用于展示的记录名称
   * @param phone 账号登录使用的手机号
   * @param departmentId 所属部门编号
   */
  public record User(Long id, String username, String name, String phone, Long departmentId) {}

  /**
   * 部门领域对象，维护名称.
   *
   * @param id 目标记录的唯一编号
   * @param name 用于展示的记录名称
   * @param parentId 父部门编号，根层使用 null
   */
  public record Department(Long id, String name, Long parentId) {}

  /**
   * 身份目录节点，包含展开提示.
   *
   * @param id 目标记录的唯一编号
   * @param code 统一身份目录中的部门编码
   * @param name 用于展示的记录名称
   * @param parentId 父部门编号，根层使用 null
   * @param hasChildren 是否存在直接子部门
   */
  public record DirectoryDepartment(
      Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /**
   * 身份目录用户，仅提供工号、姓名及所属部门.
   *
   * @param userId 用户的唯一编号
   * @param no 统一身份目录中的工号或学号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   * @param departmentName 所属部门的显示名称
   */
  public record DirectoryUser(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /**
   * 业务系统领域对象，维护名称.
   *
   * @param id 目标记录的唯一编号
   * @param systemKey 当前业务系统的唯一标识
   * @param name 用于展示的记录名称
   */
  public record System(Long id, String systemKey, String name) {}

  /**
   * 角色基本资料及部门归属.
   *
   * @param id 目标记录的唯一编号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   */
  public record Role(Long id, String name, Long departmentId) {}

  /**
   * 权限领域对象，维护名称.
   *
   * @param id 目标记录的唯一编号
   * @param systemId 权限所属的业务系统编号
   * @param code 当前系统的权限编码
   * @param name 用于展示的记录名称
   */
  public record Permission(Long id, Long systemId, String code, String name) {}

  /**
   * 角色新建和改名使用的名称输入.
   *
   * @param name 角色名称
   */
  public record Name(String name) {}

  /**
   * 管理列表及目录查询的分页数据.
   *
   * @param <T> 共享数据项的类型
   * @param items 当前页记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @param total 符合条件的记录总数
   */
  public record Page<T>(List<T> items, int page, int size, long total) {}

  /**
   * 每次 HTTP 请求内经过令牌校验的当前身份.
   *
   * @param userId 用户的唯一编号
   * @param departmentId 所属部门编号
   * @param systemKey 当前业务系统的唯一标识
   * @param sessionId 当前令牌对应的会话标识
   */
  public record Identity(Long userId, Long departmentId, String systemKey, String sessionId) {}
}
