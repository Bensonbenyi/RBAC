package cn.edu.xmu.rbac.api.shared.vo;

import cn.edu.xmu.rbac.service.port.Data;
import java.util.List;
import java.util.function.Function;

/** 管理后台响应，编号用字符串传输以保留精度. */
public final class ManagementVOs {
  /** 隐藏工具类构造入口. */
  private ManagementVOs() {}

  /**
   * 用户账号资料.
   *
   * @param id 目标记录的唯一编号
   * @param username 唯一登录账号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   */
  public record UserVO(String id, String username, String name, String departmentId) {}

  /**
   * 部门及其上级编号.
   *
   * @param id 目标记录的唯一编号
   * @param name 用于展示的记录名称
   * @param parentId 父部门编号，根层使用 null
   */
  public record DepartmentVO(String id, String name, String parentId) {}

  /**
   * 业务系统登记资料.
   *
   * @param id 目标记录的唯一编号
   * @param systemKey 当前业务系统的唯一标识
   * @param name 用于展示的记录名称
   */
  public record SystemVO(String id, String systemKey, String name) {}

  /**
   * 角色及其所属部门.
   *
   * @param id 目标记录的唯一编号
   * @param name 用于展示的记录名称
   * @param departmentId 所属部门编号
   */
  public record RoleVO(String id, String name, String departmentId) {}

  /**
   * 某个业务系统内的权限.
   *
   * @param id 目标记录的唯一编号
   * @param systemId 权限所属的业务系统编号
   * @param code 当前系统的权限编码
   * @param name 用于展示的记录名称
   */
  public record PermissionVO(String id, String systemId, String code, String name) {}

  /**
   * 管理后台从第 0 页开始的分页.
   *
   * @param <T> 共享数据项的类型
   * @param items 当前页记录
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @param total 符合条件的记录总数
   */
  public record PageVO<T>(List<T> items, int page, int size, long total) {}

  /**
   * 转换用户资料的编号格式.
   *
   * @param user 用户用例数据
   * @return 对应接口契约的响应数据
   */
  public static UserVO user(Data.User user) {
    return new UserVO(
        user.id().toString(), user.username(), user.name(), user.departmentId().toString());
  }

  /**
   * 根部门的上级编号为空.
   *
   * @param department 部门用例数据
   * @return 对应接口契约的响应数据
   */
  public static DepartmentVO department(Data.Department department) {
    Long parent = department.parentId();
    return new DepartmentVO(
        department.id().toString(), department.name(), parent == null ? null : parent.toString());
  }

  /**
   * 转换系统登记资料.
   *
   * @param system 目标系统的唯一标识
   * @return 对应接口契约的响应数据
   */
  public static SystemVO system(Data.System system) {
    return new SystemVO(system.id().toString(), system.systemKey(), system.name());
  }

  /**
   * 转换角色及其部门编号.
   *
   * @param role 拟分配的角色
   * @return 对应接口契约的响应数据
   */
  public static RoleVO role(Data.Role role) {
    return new RoleVO(role.id().toString(), role.name(), role.departmentId().toString());
  }

  /**
   * 转换权限及其系统编号.
   *
   * @param permission 待检查的界面权限码
   * @return 对应接口契约的响应数据
   */
  public static PermissionVO permission(Data.Permission permission) {
    return new PermissionVO(
        permission.id().toString(),
        permission.systemId().toString(),
        permission.code(),
        permission.name());
  }

  /**
   * 转换分页元素并保持原页码和总数.
   *
   * @param <T> 共享数据项的类型
   * @param <V> HTTP 响应项的类型
   * @param page 从 0 开始的页号
   * @param mapper 业务记录到 HTTP 响应项的转换函数
   * @return 包含当前页记录及总数的分页结果
   */
  public static <T, V> PageVO<V> page(Data.Page<T> page, Function<T, V> mapper) {
    return new PageVO<>(
        page.items().stream().map(mapper).toList(), page.page(), page.size(), page.total());
  }
}
