package cn.edu.xmu.rbac.service.auth;

import java.util.Set;

/** 前端权限目录。与 API 权限共用 RBAC 关联，分别授予、分别判断. */
public final class UiPermissions {
  /** 隐藏工具类构造入口. */
  private UiPermissions() {}

  private static final Set<String> PLATFORM =
      Set.of(
          "ui.users",
          "ui.user.roles",
          "ui.roles",
          "ui.role.create",
          "ui.role.edit",
          "ui.role.delete",
          "ui.role.permissions",
          "ui.role.inheritance",
          "ui.departments",
          "ui.systems",
          "ui.permissions");
  private static final Set<String> COURSE =
      Set.of(
          "MCO", "MTR", "MTE", "MLG", "MCOR", "MCOE", "MCOD", "MCOT", "MTRC", "MTRE", "MTRD",
          "MTRM", "MTEC", "MTEE", "MTED", "MTEL");

  /**
   * 检查权限码是否属于目标系统的界面权限目录.
   *
   * @param system 目标系统的唯一标识
   * @param code 当前系统的权限编码
   * @return 该系统登记了该界面码时为 true
   */
  public static boolean contains(String system, String code) {
    return switch (system) {
      case "rbac" -> PLATFORM.contains(code);
      case "course-project-management" -> COURSE.contains(code);
      default -> false;
    };
  }
}
