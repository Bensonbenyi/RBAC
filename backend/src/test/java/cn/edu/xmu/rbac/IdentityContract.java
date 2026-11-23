package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

abstract class IdentityContract extends ContractFixture {
  @Test
  void mapsMissingIdentityAndInvalidLoginToTheirContractErrors() {
    expect(
        401,
        "POST",
        LOGIN,
        "{\"username\":\"missing\",\"password\":\"13800000000\",\"systems\":[\"rbac\"]}",
        null);
    expect(400, "POST", LOGIN, "{\"username\":\"admin\",\"password\":\"13800000000\"}", null);
    expect(404, "GET", IDENTITY_USERS + "0" + userId("admin"), null, token);
    expect(403, "GET", IDENTITY_USERS + userId("other"), null, token);
    String course = login("admin", "course-project-management");
    expect(403, "GET", "/api/systems/options", null, course);
    assertEquals(
        "API_OPERATION_NOT_FOUND",
        expect(404, "GET", API_PERMISSIONS + "unknown", null, course).path("code").asText());
    assertTrue(
        expect(200, "GET", API_PERMISSIONS + "listTeacherCourses", null, course)
            .path("allowed")
            .asBoolean());
  }

  @Test
  @DisplayName("课程网页跨域预检通过后仍需身份和权限")
  void authorizesCourseOriginBeforeBearerAuthentication() throws Exception {
    var url = URI.create("http://localhost:" + port + "/auth/permissions/MCO");
    String origin = "https://course.example.test";
    var preflight =
        HttpRequest.newBuilder(url)
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization,x-target-system")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();
    var response = client.send(preflight, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());

    var request = HttpRequest.newBuilder(url).header("Origin", origin);
    assertEquals(
        401, client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    String course = login("viewer", "course-project-management");
    var authorized =
        client.send(
            request.header("Authorization", "Bearer " + course).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, authorized.statusCode());
    assertEquals(origin, authorized.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    assertTrue(json.readTree(authorized.body()).path("allowed").asBoolean());

    var rejected =
        HttpRequest.newBuilder(url)
            .header("Origin", "https://other.example.test")
            .header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();
    assertEquals(403, client.send(rejected, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  @DisplayName("REQ-F-01 统一身份按层级和关键词分页查询部门")
  void listsIdentityDepartmentsByLevelAndKeyword() {
    JsonNode root = expect(200, "GET", IDENTITY_DEPARTMENTS, null, token);
    assertEquals(1, root.path("page").asInt());
    assertEquals(20, root.path("pageSize").asInt());
    assertEquals(1, root.path("total").asInt());
    JsonNode department = root.path("items").get(0);
    assertTrue(department.path("id").isIntegralNumber());
    assertTrue(department.path("parentId").isNull());
    assertTrue(department.path("hasChildren").asBoolean());
    assertFalse(department.path("code").asText().isBlank());
    long id = department.path("id").asLong();
    JsonNode children =
        expect(200, "GET", IDENTITY_DEPARTMENTS + "?parentId=" + id + "&keyword=教务", null, token);
    assertEquals(id, children.path("items").get(0).path("parentId").asLong());
    assertFalse(children.path("items").get(0).path("hasChildren").asBoolean());
    insert(
        """
        insert into department(name, parent_id)
        select '教学支持', department_id from app_user where username = 'admin';
        """);
    JsonNode paged =
        expect(
            200,
            "GET",
            IDENTITY_DEPARTMENTS + "?parentId=" + id + "&page=2&pageSize=1",
            null,
            token);
    assertEquals(2, paged.path("total").asInt());
    assertEquals(departmentId("教学支持"), paged.path("items").get(0).path("id").asLong());
    JsonNode byCode =
        expect(
            200,
            "GET",
            IDENTITY_DEPARTMENTS + "?keyword=" + department.path("code").asText(),
            null,
            token);
    assertEquals(id, byCode.path("items").get(0).path("id").asLong());
    expect(400, "GET", IDENTITY_DEPARTMENTS + "?page=0", null, token);
    expect(401, "GET", IDENTITY_DEPARTMENTS, null, null);
    expect(403, "GET", IDENTITY_DEPARTMENTS, null, login("viewer", "course-project-management"));
  }

  @Test
  @DisplayName("REQ-F-01 部门用户分页只返回身份资料")
  void listsOnlyIdentityFieldsForDepartmentUsers() {
    long departmentId = store.userByUsername("admin").departmentId();
    String path = IDENTITY_DEPARTMENTS + "/" + departmentId + "/users";
    JsonNode first = expect(200, "GET", path + "?pageSize=1", null, token);
    assertEquals(3, first.path("total").asInt());
    assertEquals(1, first.path("items").size());
    JsonNode user = first.path("items").get(0);
    assertEquals("admin", user.path("no").asText());
    assertEquals("演示管理员", user.path("name").asText());
    assertEquals(departmentId, user.path("departmentId").asLong());
    assertEquals("教学部", user.path("departmentName").asText());
    assertFalse(user.has("phone"));
    assertFalse(user.has("email"));
    JsonNode filtered = expect(200, "GET", path + "?keyword=view", null, token);
    assertEquals(2, filtered.path("total").asInt());
    assertEquals("reviewer", filtered.path("items").get(0).path("no").asText());
    assertEquals("viewer", filtered.path("items").get(1).path("no").asText());
    assertEquals(
        "IDENTITY_DEPARTMENT_NOT_FOUND",
        expect(404, "GET", IDENTITY_DEPARTMENTS + "/9999999/users", null, token)
            .path("code")
            .asText());
    expect(400, "GET", path + "?pageSize=101", null, token);
    expect(403, "GET", path, null, login("viewer", "course-project-management"));
  }

  @Test
  @DisplayName("REQ-F-01 按统一身份用户标识查询工号姓名")
  void looksUpIdentityUserUnderTeacherCreationPermission() {
    String course = login("admin", "course-project-management");
    String id = Long.toString(userId("viewer"));
    JsonNode user = expect(200, "GET", IDENTITY_USERS + id, null, course);
    assertEquals(id, user.path("userId").asText());
    assertEquals("viewer", user.path("no").asText());
    assertEquals("普通教师", user.path("name").asText());
    assertFalse(user.has("phone"));
    assertEquals(
        "IDENTITY_USER_NOT_FOUND",
        expect(404, "GET", IDENTITY_USERS + "sso-unknown", null, course).path("code").asText());
    expect(403, "GET", IDENTITY_USERS + id, null, login("viewer", "course-project-management"));
  }

  @Test
  @DisplayName("REQ-F-01 登录签发全部目标系统并按目标系统判权")
  void authenticatesEachRequestedAudienceAndRevokesWholeToken() throws Exception {
    String credential =
        expect(
                200,
                "POST",
                LOGIN,
                """
                {"username":"admin","password":"13800000000",
                 "systems":["rbac","course-project-management"]}
                """,
                null)
            .path("accessToken")
            .asText();
    JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(credential.split("\\.")[1]));
    assertEquals("rbac", claims.path("aud").get(0).asText());
    assertEquals("course-project-management", claims.path("aud").get(1).asText());
    JsonNode me = expect(200, "GET", IDENTITY_ME, null, credential);
    assertEquals("admin", me.path("no").asText());
    assertEquals(Long.toString(userId("admin")), me.path("subject").asText());
    assertEquals(
        "API_OPERATION_NOT_FOUND",
        expect(404, "GET", API_PERMISSIONS + "createTerm", null, credential).path("code").asText());
    HttpResponse<String> decision =
        call("GET", API_PERMISSIONS + "createTerm", null, credential, "course-project-management");
    assertEquals(200, decision.statusCode());
    assertEquals("createTerm", json.readTree(decision.body()).path("operationId").asText());
    assertTrue(json.readTree(decision.body()).path("allowed").asBoolean());
    assertEquals(403, call("GET", IDENTITY_ME, null, credential, "unregistered").statusCode());
    expect(204, "POST", "/auth/logout", null, credential);
    assertEquals(
        "UNAUTHORIZED", expect(401, "GET", IDENTITY_ME, null, credential).path("code").asText());
    expect(
        403,
        "POST",
        LOGIN,
        """
        {"username":"viewer","password":"13800000000",
         "systems":["course-project-management","rbac"]}
        """,
        null);
    expect(
        400,
        "POST",
        LOGIN,
        """
        {"username":"admin","password":"13800000000","systems":["rbac","rbac"]}
        """,
        null);
  }

  @Test
  void isolatesUiAndApiPermissionsAndSupportsNarrowSelections() {
    var course = login("reviewer", "course-project-management");
    assertTrue(
        expect(200, "GET", "/auth/permissions/MCOR", null, course).get("allowed").asBoolean());
    assertFalse(
        expect(200, "GET", "/auth/permissions/CR", null, course).get("allowed").asBoolean());
    assertEquals(
        "API_OPERATION_NOT_FOUND",
        expect(404, "GET", API_PERMISSIONS + "MCOR", null, course).path("code").asText());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/reviewCourse", null, course)
            .get("allowed")
            .asBoolean());
    store.revokePermission(roleId("reviewer"), permissionId("course-project-management", "CR"));
    assertTrue(
        expect(200, "GET", "/auth/permissions/MCOR", null, course).get("allowed").asBoolean());
    assertFalse(
        expect(200, "GET", "/auth/api-permissions/reviewCourse", null, course)
            .get("allowed")
            .asBoolean());
    store.grantPermission(roleId("reviewer"), permissionId("course-project-management", "CR"));
    store.revokePermission(roleId("reviewer"), permissionId("course-project-management", "MCOR"));
    assertFalse(
        expect(200, "GET", "/auth/permissions/MCOR", null, course).get("allowed").asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/reviewCourse", null, course)
            .get("allowed")
            .asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/user.list", null, token)
            .get("allowed")
            .asBoolean());
    expect(404, "GET", API_PERMISSIONS + "unknown", null, token);
    for (String code : new String[] {"role.manage", "system.manage", "permission.manage"}) {
      store.revokePermission(roleId("admin"), permissionId("rbac", code));
    }
    expect(200, "GET", "/api/roles/options", null, token);
    expect(403, "GET", "/api/roles", null, token);
    expect(403, "GET", "/api/systems/options", null, token);
    store.grantPermission(roleId("admin"), permissionId("rbac", "role.manage"));
    expect(200, "GET", "/api/systems/options", null, token);
    long system = systemId("rbac");
    expect(200, "GET", "/api/permissions/options?systemId=" + system, null, token);
    expect(403, "GET", "/api/systems/" + system + "/permissions", null, token);
    expect(400, "GET", "/api/roles/options?size=101", null, token);
  }

  @Test
  void upgradesExistingGrantsOnceAndKeepsUiRevocationIndependent() throws Exception {
    // 模拟旧版本目录：保留 API 授权，移除新界面码后运行升级脚本。
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              String ui =
                  "p.code like 'ui.%' or p.code in ('MCO','MTR','MCOR','MTRC','MTRE','MTRD')";
              entities
                  .createQuery(
                      "delete from RolePermissionEntity link where link.id.permissionId in "
                          + "(select p.id from PermissionEntity p where "
                          + ui
                          + ")")
                  .executeUpdate();
              entities.createQuery("delete from PermissionEntity p where " + ui).executeUpdate();
            });
    // 执行真实 Flyway 迁移资源，验证已有数据库升级。
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__ui_permissions.sql"))
        .execute(dataSource);
    assertTrue(
        expect(200, "GET", "/auth/permissions/ui.users", null, token).get("allowed").asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/user.list", null, token)
            .get("allowed")
            .asBoolean());
    store.revokePermission(roleId("admin"), permissionId("rbac", "ui.users"));
    assertFalse(
        expect(200, "GET", "/auth/permissions/ui.users", null, token).get("allowed").asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/user.list", null, token)
            .get("allowed")
            .asBoolean());
  }

  @Test
  void authenticatesAndRejectsBadCredentials() {
    expect(401, "GET", "/auth/me", null, null);
    expect(
        401,
        "POST",
        "/auth/login",
        "{\"username\":\"admin\",\"password\":\"13900000000\",\"systems\":[\"rbac\"]}",
        null);
    var me = expect(200, "GET", "/auth/me", null, token);
    assertEquals("admin", me.get("no").asText());
    assertTrue(me.get("permissions").toString().contains("ui.users"));
    expect(
        403,
        "POST",
        "/auth/login",
        "{\"username\":\"viewer\",\"password\":\"13800000000\",\"systems\":[\"rbac\"]}",
        null);
  }

  @Test
  void tokensContainIdentityAndAudienceWithoutPermissions() {
    var claims = json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    assertEquals(
        "rbac",
        (claims.get("aud").isArray() ? claims.get("aud").get(0) : claims.get("aud")).asText());
    assertFalse(claims.has("permissions"));
    assertFalse(claims.has("roles"));
    assertTrue(claims.has("jti"));
    expect(401, "GET", "/auth/me", null, token.substring(0, token.length() - 5) + "xxxxx");
  }

  @Test
  void logoutAndExpiryInvalidateSession() {
    String course = login("viewer", "course-project-management");
    expect(204, "POST", "/auth/logout", null, course);
    expect(401, "GET", "/auth/me", null, course);
    course = login("viewer", "course-project-management");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status ->
                entities
                    .createQuery(
                        "update LoginSessionEntity s set expiresAt=:past where userId=:user")
                    .setParameter("past", Instant.now().minusSeconds(60))
                    .setParameter("user", userId("viewer"))
                    .executeUpdate());
    expect(401, "GET", "/auth/me", null, course);
  }
}
