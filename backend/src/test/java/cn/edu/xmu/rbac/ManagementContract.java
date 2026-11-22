package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

abstract class ManagementContract extends ContractFixture {
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
  void directoryCrudHonoursReferencesAndCaseSensitiveCodes() {
    expect(405, "POST", "/api/departments", "{\"name\":\"新部门\"}", token);
    expect(200, "GET", "/api/departments", null, token);
    var sys =
        expect(201, "POST", "/api/systems", "{\"systemKey\":\"test\",\"name\":\"测试\"}", token);
    String sid = sys.get("id").asText();
    expect(405, "PATCH", "/api/systems/" + sid, "{\"name\":\"新名称\"}", token);
    var permission =
        expect(
            201,
            "POST",
            "/api/systems/" + sid + "/permissions",
            "{\"code\":\"UPPER\",\"name\":\"测试权限\"}",
            token);
    String pid = permission.get("id").asText();
    expect(409, "DELETE", "/api/systems/" + sid, null, token);
    expect(204, "PATCH", "/api/permissions/" + pid, "{\"name\":\"重命名\"}", token);
    expect(204, "DELETE", "/api/permissions/" + pid, null, token);
    expect(204, "DELETE", "/api/systems/" + sid, null, token);
  }
}
