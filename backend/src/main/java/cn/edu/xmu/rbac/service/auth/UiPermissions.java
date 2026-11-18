package cn.edu.xmu.rbac.service.auth;

import java.util.Set;

/** 前端权限目录。与 API 权限共用 RBAC 关联，分别授予、分别判断. */
public final class UiPermissions {
  private UiPermissions() {}

  public static final Set<String> PLATFORM =
      Set.of(
          "ui.users",
          "ui.user.create",
          "ui.user.edit",
          "ui.user.delete",
          "ui.user.roles",
          "ui.roles",
          "ui.role.create",
          "ui.role.edit",
          "ui.role.delete",
          "ui.role.permissions",
          "ui.role.inheritance",
          "ui.departments",
          "ui.department.create",
          "ui.department.edit",
          "ui.department.delete",
          "ui.systems",
          "ui.system.create",
          "ui.system.edit",
          "ui.system.delete",
          "ui.permissions",
          "ui.permission.create",
          "ui.permission.edit",
          "ui.permission.delete");
  public static final Set<String> COURSE = Set.of("MCO", "MTR", "MCOR", "MTRC", "MTRE", "MTRD");

  /** contains 的用例处理. */
  public static boolean contains(String system, String code) {
    return switch (system) {
      case "rbac" -> PLATFORM.contains(code);
      case "course-project-management" -> COURSE.contains(code);
      default -> false;
    };
  }
}
