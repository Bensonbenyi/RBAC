package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.RoleInheritanceEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.RolePermissionEntity;
import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserRoleEntity;
import org.junit.jupiter.api.Test;

abstract class ManagementContract extends ContractFixture {
  @Test
  void returnsNoEffectivePermissionsForUserWithoutRoles() {
    assertTrue(effectiveAccess.permissionsForUser(userId("other"), "rbac").isEmpty());
  }

  @Test
  void loadsCompositeKeysFromPersistedAuthorizationRows() {
    long user = userId("viewer");
    long role = roleId("viewer");
    long permission = permissionId("rbac", "ui.users");
    long junior = roleId("teacher");
    grant(201, role, permission);
    inherit(201, role, junior);
    assertEquals(
        new UserRoleEntity.Key(user, role),
        entities.find(UserRoleEntity.class, new UserRoleEntity.Key(user, role)).getId());
    assertEquals(
        new RolePermissionEntity.Key(role, permission),
        entities
            .find(RolePermissionEntity.class, new RolePermissionEntity.Key(role, permission))
            .getId());
    assertEquals(
        new RoleInheritanceEntity.Key(role, junior),
        entities
            .find(RoleInheritanceEntity.class, new RoleInheritanceEntity.Key(role, junior))
            .getId());
  }

  @Test
  void listsDirectGrantsSeparatelyFromInheritedPermissions() {
    long user = userId("viewer");
    long role = roleId("viewer");
    long permission = permissionId("rbac", "ui.users");
    grant(201, role, permission);
    assertTrue(
        expect(200, "GET", "/api/users/" + user + "/roles", null, token)
            .toString()
            .contains("viewer"));
    assertTrue(
        expect(200, "GET", "/api/roles/" + role + "/permissions", null, token)
            .toString()
            .contains("ui.users"));
  }

  @Test
  void preservesHttpRoutingErrors() {
    expect(404, "GET", "/api/unknown-resource", null, token);
    var response = expect(405, "PUT", "/api/users", "{}", token);
    assertEquals("METHOD_NOT_ALLOWED", response.get("code").asText());
  }

  @Test
  void validatesInputAndRemovedEndpoints() {
    expect(405, "POST", "/api/users", "{}", token);
    expect(404, "GET", "/api/users/" + userId("viewer"), null, token);
    expect(404, "PATCH", "/api/users/" + userId("viewer"), "{}", token);
    expect(404, "DELETE", "/api/users/" + userId("viewer"), null, token);
    expect(405, "PATCH", "/auth/me", "{}", token);
    expect(405, "GET", "/api/roles/" + roleId("reviewer"), null, token);
    expect(400, "GET", "/api/users?size=101", null, token);
    expect(400, "GET", "/api/users/-1/roles", null, token);
    expect(
        400,
        "POST",
        "/api/roles/" + roleId("reviewer") + "/permissions",
        "{\"permissionId\":1,\"systemId\":2}",
        token);
  }

  @Test
  void enforcesDirectDepartmentScope() {
    var list = expect(200, "GET", "/api/users", null, token);
    assertEquals(3, list.get("total").asInt());
    assertFalse(list.path("items").get(0).has("phone"));
    expect(403, "GET", "/api/users/" + userId("other") + "/roles", null, token);
    long otherDept = store.userByUsername("other").departmentId();
    long otherRole = store.createRole(otherDept, "其他部门角色").id();
    expect(
        403,
        "POST",
        "/api/users/" + userId("viewer") + "/roles",
        "{\"roleId\":\"" + otherRole + "\"}",
        token);
  }

  @Test
  void assignsAndRevokesRoleWithConflictAndIdempotence() {
    var role = expect(201, "POST", "/api/roles", "{\"name\":\"临时角色\"}", token);
    String roleId = role.get("id").asText();
    expect(204, "PATCH", "/api/roles/" + roleId, "{\"name\":\"已改名\"}", token);
    assertTrue(expect(200, "GET", "/api/roles", null, token).toString().contains("已改名"));
    String path = "/api/users/" + userId("viewer") + "/roles";
    expect(201, "POST", path, "{\"roleId\":\"" + roleId + "\"}", token);
    assertEquals(
        "CONFLICT",
        expect(409, "POST", path, "{\"roleId\":\"" + roleId + "\"}", token).get("code").asText());
    expect(409, "DELETE", "/api/roles/" + roleId, null, token);
    expect(204, "DELETE", path + "/" + roleId, null, token);
    expect(204, "DELETE", path + "/" + roleId, null, token);
    expect(204, "DELETE", "/api/roles/" + roleId, null, token);
  }

  @Test
  void readsCatalogsWithoutWriteEndpoints() {
    expect(405, "POST", "/api/departments", "{}", token);
    expect(200, "GET", "/api/departments", null, token);
    expect(405, "POST", "/api/systems", "{}", token);
    assertEquals(2, expect(200, "GET", "/api/systems", null, token).path("total").asInt());
    var system = expect(200, "GET", "/api/systems", null, token).path("items").get(0);
    String sid = system.path("id").asText();
    String permissions = "/api/systems/" + sid + "/permissions";
    assertTrue(expect(200, "GET", permissions, null, token).path("total").asInt() > 0);
    expect(405, "POST", permissions, "{}", token);
    expect(404, "DELETE", "/api/systems/" + sid, null, token);
    expect(404, "PATCH", "/api/permissions/1", "{}", token);
    expect(404, "DELETE", "/api/permissions/1", null, token);
  }
}
