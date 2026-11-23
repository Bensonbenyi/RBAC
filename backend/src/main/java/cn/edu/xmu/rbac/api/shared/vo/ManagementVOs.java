package cn.edu.xmu.rbac.api.shared.vo;

import cn.edu.xmu.rbac.service.port.Data;
import java.util.List;
import java.util.function.Function;

/** 管理后台响应，编号用字符串传输以保留精度. */
public final class ManagementVOs {
  private ManagementVOs() {}

  /** 用户账号资料. */
  public record UserVO(String id, String username, String name, String departmentId) {}

  /** 部门及其上级编号. */
  public record DepartmentVO(String id, String name, String parentId) {}

  /** 业务系统登记资料. */
  public record SystemVO(String id, String systemKey, String name) {}

  /** 角色及其所属部门. */
  public record RoleVO(String id, String name, String departmentId) {}

  /** 某个业务系统内的权限. */
  public record PermissionVO(String id, String systemId, String code, String name) {}

  /** 管理后台从第 0 页开始的分页. */
  public record PageVO<T>(List<T> items, int page, int size, long total) {}

  /** 转换用户资料的编号格式. */
  public static UserVO user(Data.User user) {
    return new UserVO(
        user.id().toString(), user.username(), user.name(), user.departmentId().toString());
  }

  /** 根部门的上级编号为空. */
  public static DepartmentVO department(Data.Department department) {
    Long parent = department.parentId();
    return new DepartmentVO(
        department.id().toString(), department.name(), parent == null ? null : parent.toString());
  }

  /** 转换系统登记资料. */
  public static SystemVO system(Data.System system) {
    return new SystemVO(system.id().toString(), system.systemKey(), system.name());
  }

  /** 转换角色及其部门编号. */
  public static RoleVO role(Data.Role role) {
    return new RoleVO(role.id().toString(), role.name(), role.departmentId().toString());
  }

  /** 转换权限及其系统编号. */
  public static PermissionVO permission(Data.Permission permission) {
    return new PermissionVO(
        permission.id().toString(),
        permission.systemId().toString(),
        permission.code(),
        permission.name());
  }

  /** 转换分页元素并保持原页码和总数. */
  public static <T, V> PageVO<V> page(Data.Page<T> page, Function<T, V> mapper) {
    return new PageVO<>(
        page.items().stream().map(mapper).toList(), page.page(), page.size(), page.total());
  }
}
