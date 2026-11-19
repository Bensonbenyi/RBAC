package cn.edu.xmu.rbac.service.auth;

import java.util.Map;
import java.util.Set;

/** 将两个接入系统的 operationId 映射到后端权限；未知操作拒绝. */
public final class OperationPermissions {
  private OperationPermissions() {}

  // 来自课程 OpenAPI 19fbd1a 的全局操作许可。对象级规则由课程服务判断。
  private static final Map<String, String> COURSE =
      Map.ofEntries(
          Map.entry("clearCurrentTerm", "TCM"),
          Map.entry("createTeacher", "TEC"),
          Map.entry("createTerm", "TC"),
          Map.entry("deleteCourse", "CD"),
          Map.entry("deleteTeacher", "TED"),
          Map.entry("deleteTerm", "TD"),
          Map.entry("getCourse", "CV"),
          Map.entry("listAdminCourses", "CV"),
          Map.entry("listAuditLogs", "AV"),
          Map.entry("listIdentityDepartmentUsers", "TEC"),
          Map.entry("listIdentityDepartments", "TEC"),
          Map.entry("listTeachers", "TV"),
          Map.entry("lookupIdentityUser", "TEC"),
          Map.entry("replaceCurrentTerm", "TCM"),
          Map.entry("resetCourseToApplication", "CRS"),
          Map.entry("reviewCourse", "CR"),
          Map.entry("setClassTeachers", "CTM"),
          Map.entry("setCourseLeaders", "CTM"),
          Map.entry("updateCourse", "CE"),
          Map.entry("updateTeacher", "TEE"),
          Map.entry("updateTerm", "TE"));

  private static final Set<String> COURSE_AUTHENTICATED =
      Set.of(
          "addStudent",
          "bookOralSlot",
          "cancelBooking",
          "cancelGroupJoinRequest",
          "checkIdentityPermission",
          "copyCourse",
          "createClasses",
          "createCourse",
          "createGroupAsStudent",
          "createGroupAsTeacher",
          "createGroupJoinRequest",
          "createGroupOralRecord",
          "createMilestone",
          "createOralSlot",
          "createStudentOralRecord",
          "decideGroupJoinRequest",
          "deleteClass",
          "deleteClassGroupRule",
          "deleteMilestone",
          "deleteOralSlot",
          "dissolveGroup",
          "downloadFile",
          "exportClassGrades",
          "getApiAuthority",
          "getClassGrades",
          "getClassGroupRule",
          "getCourseGroupRule",
          "getCurrentUser",
          "getGroup",
          "getGroupSubmission",
          "getIdentityCurrentUser",
          "getMyGrades",
          "getMyMilestoneSubmission",
          "getSubmissionById",
          "importRoster",
          "leaveGroup",
          "listClassGroups",
          "listClassStudents",
          "listClasses",
          "listCourseGroups",
          "listGroupJoinRequests",
          "listMilestones",
          "listOralSlots",
          "listOralStatus",
          "listStudentCourses",
          "listSubmissions",
          "listTeacherCourses",
          "listTerms",
          "login",
          "logout",
          "removeGroupMemberAsLeader",
          "removeStudent",
          "reviewSubmission",
          "searchStudentCandidates",
          "setClassGroupRule",
          "submitCourseReview",
          "submitMaterials",
          "submitMyMaterials",
          "summarizeOralMemo",
          "transferGroupLeader",
          "updateClass",
          "updateCourseDescription",
          "updateCourseGroupRule",
          "updateGroup",
          "updateMilestone",
          "updateOralRecord");

  private static final Map<String, String> PLATFORM =
      Map.ofEntries(
          Map.entry("auth.me", "login"),
          Map.entry("auth.updateMe", "login"),
          Map.entry("auth.logout", "login"),
          Map.entry("auth.check", "login"),
          Map.entry("auth.api", "login"),
          Map.entry("getIdentityCurrentUser", "login"),
          Map.entry("logout", "login"),
          Map.entry("checkIdentityPermission", "login"),
          Map.entry("getApiAuthority", "login"),
          Map.entry("lookupIdentityUser", "user.view"),
          Map.entry("listIdentityDepartments", "department.manage"),
          Map.entry("listIdentityDepartmentUsers", "user.view"),
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
          Map.entry("role.inheritedRoles", "role.manage"),
          Map.entry("role.effectivePermissions", "role.manage"),
          Map.entry("role.inherit", "role.manage"),
          Map.entry("role.uninherit", "role.manage"),
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

  /** 按目标系统映射操作标识到 API 权限码，未知操作返回 null. */
  public static String code(String system, String operation) {
    return switch (system) {
      case "course-project-management" ->
          COURSE_AUTHENTICATED.contains(operation) ? "login" : COURSE.get(operation);
      case "rbac" -> PLATFORM.get(operation);
      default -> null;
    };
  }
}
