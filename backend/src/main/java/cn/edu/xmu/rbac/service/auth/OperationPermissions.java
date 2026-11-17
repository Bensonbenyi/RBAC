package cn.edu.xmu.rbac.service.auth;

import java.util.Map;

/** 将两个接入系统的 operationId 映射到后端权限；未知操作拒绝. */
public final class OperationPermissions {
  private OperationPermissions() {}

  private static final Map<String, String> COURSE =
      Map.ofEntries(
          Map.entry("getCurrentUser", "login"),
          Map.entry("createTerm", "TC"),
          Map.entry("updateTerm", "TE"),
          Map.entry("deleteTerm", "TD"),
          Map.entry("listTerms", "login"),
          Map.entry("listAdminCourses", "CV"),
          Map.entry("getCourse", "CV"),
          Map.entry("reviewCourse", "CR"));

  private static final Map<String, String> PLATFORM =
      Map.ofEntries(
          Map.entry("auth.me", "login"),
          Map.entry("auth.updateMe", "login"),
          Map.entry("auth.logout", "login"),
          Map.entry("auth.check", "login"),
          Map.entry("auth.api", "login"),
          Map.entry("identity.get", "UL"),
          Map.entry("role.options", "user.role.assign"),
          Map.entry("permission.options", "role.manage"),
          Map.entry("user.list", "user.view"),
          Map.entry("user.get", "user.view"),
          Map.entry("user.create", "user.create"),
          Map.entry("user.update", "user.edit"),
          Map.entry("user.delete", "user.delete"),
          Map.entry("user.roles", "user.role.assign"),
          Map.entry("user.assign", "user.role.assign"),
          Map.entry("user.revoke", "user.role.assign"),
          Map.entry("role.list", "role.manage"),
          Map.entry("role.get", "role.manage"),
          Map.entry("role.create", "role.manage"),
          Map.entry("role.update", "role.manage"),
          Map.entry("role.delete", "role.manage"),
          Map.entry("role.permissions", "role.manage"),
          Map.entry("role.grant", "role.manage"),
          Map.entry("role.revoke", "role.manage"),
          Map.entry("department.list", "department.manage"),
          Map.entry("department.create", "department.manage"),
          Map.entry("department.update", "department.manage"),
          Map.entry("department.delete", "department.manage"),
          Map.entry("system.list", "system.manage"),
          Map.entry("system.create", "system.manage"),
          Map.entry("system.update", "system.manage"),
          Map.entry("system.delete", "system.manage"),
          Map.entry("permission.list", "permission.manage"),
          Map.entry("permission.create", "permission.manage"),
          Map.entry("permission.update", "permission.manage"),
          Map.entry("permission.delete", "permission.manage"));

  /** code 的用例处理. */
  public static String code(String system, String operation) {
    return switch (system) {
      case "course-project-management" -> COURSE.get(operation);
      case "rbac" -> PLATFORM.get(operation);
      default -> null;
    };
  }
}
