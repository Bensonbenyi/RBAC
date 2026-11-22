package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

abstract class RbacContract extends ContractFixture {
  @Test
  void revocationIsVisibleToExistingTokenAndSystemsAreIsolated() {
    String course = login("reviewer", "course-project-management");
    String query = "/auth/api-permissions/reviewCourse";
    assertTrue(expect(200, "GET", query, null, course).get("allowed").asBoolean());
    String path =
        "/api/roles/"
            + roleId("reviewer")
            + "/permissions/"
            + permissionId("course-project-management", "CR");
    expect(204, "DELETE", path, null, token);
    assertFalse(expect(200, "GET", query, null, course).get("allowed").asBoolean());
    grant(201, roleId("reviewer"), permissionId("course-project-management", "CR"));
    grant(409, roleId("reviewer"), permissionId("course-project-management", "CR"));
    grant(404, roleId("reviewer"), 9999999);
    assertTrue(expect(200, "GET", query, null, course).get("allowed").asBoolean());
    expect(403, "GET", "/api/users", null, course);
    expect(404, "GET", API_PERMISSIONS + "unknown", null, course);
  }

  @Test
  void inheritsAcrossMultiplePathsAndRevokesForExistingTokens() {
    Long dept = store.user(userId("admin")).departmentId();
    var senior = store.createRole(dept, "senior");
    final var left = store.createRole(dept, "left");
    final var right = store.createRole(dept, "right");
    var base = store.createRole(dept, "base");
    insert(
        """
        insert into app_user(username, name, phone, department_id)
        select 'heir', '继承验证', '13800000000', department_id
        from app_user where username = 'admin';
        """);
    store.assignRole(userId("heir"), senior.id());
    store.grantPermission(base.id(), permissionId("rbac", "login"));
    store.grantPermission(base.id(), permissionId("rbac", "user.view"));
    store.grantPermission(base.id(), permissionId("rbac", "ui.users"));
    for (var edge :
        new Long[][] {
          {senior.id(), left.id()},
          {senior.id(), right.id()},
          {left.id(), base.id()},
          {right.id(), base.id()}
        }) {
      inherit(201, edge[0], edge[1]);
    }
    String heir = login("heir", "rbac");
    expect(200, "GET", "/api/users", null, heir);
    assertEquals(
        3,
        expect(200, "GET", "/api/roles/" + senior.id() + "/effective-permissions", null, token)
            .size());
    assertTrue(
        expect(200, "GET", "/auth/permissions/ui.users", null, heir).get("allowed").asBoolean());
    expect(
        403,
        "POST",
        "/auth/login",
        "{\"username\":\"heir\",\"password\":\"13800000000\","
            + "\"systems\":[\"course-project-management\"]}",
        null);
    expect(204, "DELETE", "/api/roles/" + left.id() + "/inherited-roles/" + base.id(), null, token);
    expect(200, "GET", "/api/users", null, heir);
    // 保留 login，只撤销继承来的业务权限，旧令牌立即被拒绝。
    store.grantPermission(senior.id(), permissionId("rbac", "login"));
    expect(
        204, "DELETE", "/api/roles/" + right.id() + "/inherited-roles/" + base.id(), null, token);
    expect(403, "GET", "/api/users", null, heir);
    assertFalse(
        expect(200, "GET", "/auth/permissions/ui.users", null, heir).get("allowed").asBoolean());
  }

  @Test
  void rejectsHierarchyCyclesScopeAndReferences() {
    Long dept = store.user(userId("admin")).departmentId();
    var a = store.createRole(dept, "A");
    var b = store.createRole(dept, "B");
    final var c = store.createRole(dept, "C");
    inherit(201, a.id(), b.id());
    inherit(409, a.id(), b.id());
    assertEquals(
        1, expect(200, "GET", "/api/roles/" + a.id() + "/inherited-roles", null, token).size());
    inherit(201, b.id(), c.id());
    inherit(409, c.id(), a.id());
    inherit(409, a.id(), a.id());
    expect(409, "DELETE", "/api/roles/" + b.id(), null, token);
    var other = store.createRole(store.user(userId("other")).departmentId(), "foreign");
    inherit(403, a.id(), other.id());
    expect(403, "DELETE", "/api/roles/" + a.id() + "/inherited-roles/" + other.id(), null, token);
    inherit(404, a.id(), 9999999);
    inherit(400, a.id(), 0);
    expect(
        403,
        "GET",
        "/api/roles/" + a.id() + "/inherited-roles",
        null,
        login("viewer", "course-project-management"));
    String ab = "/api/roles/" + a.id() + "/inherited-roles/" + b.id();
    expect(204, "DELETE", ab, null, token);
    expect(204, "DELETE", ab, null, token);
  }
}
