package cn.edu.xmu.rbac.api.auth;

import java.util.Map;

public final class OperationPermissions {
  private OperationPermissions() {}

  private static final Map<String, String> COURSE =
      Map.ofEntries(
          Map.entry("createTerm", "TC"),
          Map.entry("updateTerm", "TE"),
          Map.entry("deleteTerm", "TD"),
          Map.entry("listTerms", "login"),
          Map.entry("listAdminCourses", "CV"),
          Map.entry("getCourse", "CV"),
          Map.entry("reviewCourse", "CR"));

  public static String code(String system, String operation) {
    return "course-project-management".equals(system) ? COURSE.get(operation) : null;
  }
}
