package cn.edu.xmu.rbac.service.auth;

import java.util.Set;

/** 前端权限目录。与 API 权限共用 RBAC 关联，分别授予、分别判断. */
public final class UiPermissions {
  private UiPermissions() {}

  public static final Set<String> PLATFORM =
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
  public static final Set<String> COURSE =
      Set.of(
          "MCO", "MTR", "MTE", "MLG", "MCOR", "MCOE", "MCOD", "MCOT", "MTRC", "MTRE", "MTRD",
          "MTRM", "MTEC", "MTEE", "MTED", "MTEL");

  /** 判断权限码是否属于指定系统登记的界面权限目录. */
  public static boolean contains(String system, String code) {
    return switch (system) {
      case "rbac" -> PLATFORM.contains(code);
      case "course-project-management" -> COURSE.contains(code);
      default -> false;
    };
  }
}
