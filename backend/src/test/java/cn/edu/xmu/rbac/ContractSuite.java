package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.edu.xmu.rbac.infrastructure.bootstrap.DemoData;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

abstract class ContractSuite {
  private static final String LOGIN = "/auth/login";
  private static final String IDENTITY_ME = "/auth/me";
  private static final String IDENTITY_DEPARTMENTS = "/auth/departments";
  private static final String IDENTITY_USERS = "/auth/users/";
  private static final String API_PERMISSIONS = "/auth/api-permissions/";

  static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("rbac_test");
  static final Path KEYS;

  static {
    try {
      KEYS = Files.createTempDirectory("rbac-test-keys");
      var pair = KeyPairGenerator.getInstance("RSA");
      pair.initialize(2048);
      var key = pair.generateKeyPair();
      writeKey("private.pem", "PRIVATE KEY", key.getPrivate().getEncoded());
      writeKey("public.pem", "PUBLIC KEY", key.getPublic().getEncoded());
      MYSQL.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static void writeKey(String name, String type, byte[] bytes) throws Exception {
    Files.writeString(
        KEYS.resolve(name),
        "-----BEGIN "
            + type
            + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
            + "\n-----END "
            + type
            + "-----\n");
  }

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", MYSQL::getJdbcUrl);
    p.add("spring.datasource.username", MYSQL::getUsername);
    p.add("spring.datasource.password", MYSQL::getPassword);
    p.add("app.jwt.private-key", () -> KEYS.resolve("private.pem").toUri().toString());
    p.add("app.jwt.public-key", () -> KEYS.resolve("public.pem").toUri().toString());
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ManagementStore store;
  final JsonMapper json = JsonMapper.builder().build();
  final HttpClient client = HttpClient.newHttpClient();
  String token;

  @BeforeEach
  void seed() {
    jdbc.update("UPDATE department SET parent_id=NULL");
    for (String table :
        new String[] {
          "login_session",
          "role_inheritance",
          "user_role",
          "role_permission",
          "permission",
          "business_system",
          "app_user",
          "`role`",
          "department"
        }) {
      jdbc.update("DELETE FROM " + table);
    }
    new DemoData(store, jdbc, "13800000000").run(null);
    token = login("admin", "rbac");
  }

  HttpResponse<String> call(String method, String path, String body, String credential)
      throws Exception {
    return call(method, path, body, credential, null);
  }

  HttpResponse<String> call(
      String method, String path, String body, String credential, String targetSystem)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (targetSystem != null) {
      builder.header("X-Target-System", targetSystem);
    }
    if (credential != null) {
      builder.header("Authorization", "Bearer " + credential);
    }
    builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  JsonNode expect(int status, String method, String path, String body, String credential) {
    try {
      var response = call(method, path, body, credential);
      assertEquals(status, response.statusCode(), response.body());
      return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  String login(String username, String system) {
    return expect(
            200,
            "POST",
            "/auth/login",
            "{\"username\":\""
                + username
                + "\",\"password\":\"13800000000\",\"systems\":[\""
                + system
                + "\"]}",
            null)
        .get("accessToken")
        .asText();
  }

  long userId(String username) {
    return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
  }

  long roleId(String name) {
    return jdbc.queryForObject("SELECT id FROM `role` WHERE name=?", Long.class, name);
  }

  long permissionId(String system, String code) {
    return jdbc.queryForObject(
        "SELECT p.id FROM permission p JOIN business_system s ON s.id=p.system_id WHERE"
            + " s.system_key=? AND p.code=?",
        Long.class,
        system,
        code);
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
    long departmentId =
        jdbc.queryForObject(
            "SELECT department_id FROM app_user WHERE username='admin'", Long.class);
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
        {"username":"admin","password":"13800000000","systems":["rbac","course-project-management"]}
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
    jdbc.update(
        "DELETE FROM role_permission WHERE role_id=? AND permission_id=?",
        roleId("reviewer"),
        permissionId("course-project-management", "CR"));
    assertTrue(
        expect(200, "GET", "/auth/permissions/MCOR", null, course).get("allowed").asBoolean());
    assertFalse(
        expect(200, "GET", "/auth/api-permissions/reviewCourse", null, course)
            .get("allowed")
            .asBoolean());
    store.grantPermission(roleId("reviewer"), permissionId("course-project-management", "CR"));
    jdbc.update(
        "DELETE FROM role_permission WHERE role_id=? AND permission_id=?",
        roleId("reviewer"),
        permissionId("course-project-management", "MCOR"));
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
      jdbc.update(
          "DELETE FROM role_permission WHERE role_id=? AND permission_id=?",
          roleId("admin"),
          permissionId("rbac", code));
    }
    expect(200, "GET", "/api/roles/options", null, token);
    expect(403, "GET", "/api/roles", null, token);
    expect(403, "GET", "/api/systems/options", null, token);
    store.grantPermission(roleId("admin"), permissionId("rbac", "role.manage"));
    expect(200, "GET", "/api/systems/options", null, token);
    long system =
        jdbc.queryForObject("SELECT id FROM business_system WHERE system_key='rbac'", Long.class);
    expect(200, "GET", "/api/permissions/options?systemId=" + system, null, token);
    expect(403, "GET", "/api/systems/" + system + "/permissions", null, token);
    expect(400, "GET", "/api/roles/options?size=101", null, token);
  }

  @Test
  void upgradesExistingGrantsOnceAndKeepsUiRevocationIndependent() throws Exception {
    // 模拟旧版本目录：保留 API 授权，移除新界面码后运行升级脚本。
    jdbc.update(
        "DELETE rp FROM role_permission rp JOIN permission p ON p.id=rp.permission_id"
            + " WHERE p.code LIKE 'ui.%' OR p.code IN ('MCO','MTR','MCOR','MTRC','MTRE','MTRD')");
    jdbc.update(
        "DELETE FROM permission WHERE code LIKE 'ui.%'"
            + " OR code IN ('MCO','MTR','MCOR','MTRC','MTRE','MTRD')");
    try (var connection = jdbc.getDataSource().getConnection()) {
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          connection,
          new org.springframework.core.io.ClassPathResource("db/migration/V2__ui_permissions.sql"));
    }
    assertTrue(
        expect(200, "GET", "/auth/permissions/ui.users", null, token).get("allowed").asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/user.list", null, token)
            .get("allowed")
            .asBoolean());
    jdbc.update(
        "DELETE FROM role_permission WHERE role_id=? AND permission_id=?",
        roleId("admin"),
        permissionId("rbac", "ui.users"));
    assertFalse(
        expect(200, "GET", "/auth/permissions/ui.users", null, token).get("allowed").asBoolean());
    assertTrue(
        expect(200, "GET", "/auth/api-permissions/user.list", null, token)
            .get("allowed")
            .asBoolean());
  }

  @Test
  void preservesHttpRoutingErrors() {
    expect(404, "GET", "/api/unknown-resource", null, token);
    var response = expect(405, "PUT", "/api/users", "{}", token);
    assertEquals("METHOD_NOT_ALLOWED", response.get("code").asText());
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
  void validatesInputAndProtectedFields() {
    expect(
        400,
        "POST",
        "/api/users",
        "{\"username\":\"new\",\"name\":\"新用户\",\"phone\":\"123\"}",
        token);
    expect(400, "PATCH", "/api/users/" + userId("admin"), "{\"departmentId\":2}", token);
    expect(400, "PATCH", "/api/users/" + userId("admin"), "{}", token);
    expect(400, "GET", "/api/users?size=101", null, token);
    expect(400, "GET", "/api/users/-1", null, token);
    expect(
        400,
        "POST",
        "/auth/login",
        "{\"username\":\"admin\",\"password\":\"13800000000\"," + "\"systems\":[]}",
        null);
  }

  @Test
  void createsUpdatesAndDeletesUserWithGeneratedIdentity() {
    var user =
        expect(
            201,
            "POST",
            "/api/users",
            "{\"username\":\"new\",\"name\":\"新用户\",\"phone\":\"13900000000\"}",
            token);
    String id = user.get("id").asText();
    assertTrue(user.get("id").isString());
    expect(204, "PATCH", "/api/users/" + id, "{\"name\":\"新姓名\"}", token);
    assertEquals(
        "13900000000", expect(200, "GET", "/api/users/" + id, null, token).get("phone").asText());
    expect(
        409,
        "POST",
        "/api/users",
        "{\"username\":\"new\",\"name\":\"重复\",\"phone\":\"13900000000\"}",
        token);
    expect(204, "DELETE", "/api/users/" + id, null, token);
    expect(404, "GET", "/api/users/" + id, null, token);
  }

  @Test
  void enforcesDirectDepartmentScope() {
    var list = expect(200, "GET", "/api/users", null, token);
    assertEquals(3, list.get("total").asInt());
    expect(403, "GET", "/api/users/" + userId("other"), null, token);
    expect(403, "PATCH", "/api/users/" + userId("other"), "{\"name\":\"越界\"}", token);
    long otherDept =
        jdbc.queryForObject(
            "SELECT department_id FROM app_user WHERE username='other'", Long.class);
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
    expect(204, "PUT", path, null, token);
    expect(204, "PUT", path, null, token);
    expect(404, "PUT", "/api/roles/" + roleId("reviewer") + "/permissions/9999999", null, token);
    assertTrue(expect(200, "GET", query, null, course).get("allowed").asBoolean());
    expect(403, "GET", "/api/users", null, course);
    expect(404, "GET", API_PERMISSIONS + "unknown", null, course);
  }

  @Test
  void logoutExpiryAndDeletionInvalidateSession() {
    String course = login("viewer", "course-project-management");
    expect(204, "POST", "/auth/logout", null, course);
    expect(401, "GET", "/auth/me", null, course);
    course = login("viewer", "course-project-management");
    jdbc.update(
        "UPDATE login_session SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE user_id=?",
        userId("viewer"));
    expect(401, "GET", "/auth/me", null, course);
    course = login("viewer", "course-project-management");
    expect(204, "DELETE", "/api/users/" + userId("viewer"), null, token);
    expect(401, "GET", "/auth/me", null, course);
  }

  @Test
  void directoryCrudHonoursReferencesAndCaseSensitiveCodes() {
    var dept = expect(201, "POST", "/api/departments", "{\"name\":\"新部门\"}", token);
    String id = dept.get("id").asText();
    expect(204, "PATCH", "/api/departments/" + id, "{\"name\":\"新名称\"}", token);
    expect(204, "DELETE", "/api/departments/" + id, null, token);
    var sys =
        expect(201, "POST", "/api/systems", "{\"systemKey\":\"test\",\"name\":\"测试\"}", token);
    String sid = sys.get("id").asText();
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

  @Test
  void inheritsAcrossMultiplePathsAndRevokesForExistingTokens() {
    Long dept = store.user(userId("admin")).departmentId();
    var senior = store.createRole(dept, "senior");
    final var left = store.createRole(dept, "left");
    final var right = store.createRole(dept, "right");
    var base = store.createRole(dept, "base");
    var isolated =
        store.createUser(
            dept, new cn.edu.xmu.rbac.service.port.Data.CreateUser("heir", "继承用户", "13800000000"));
    store.assignRole(isolated.id(), senior.id());
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
      expect(204, "PUT", "/api/roles/" + edge[0] + "/inherited-roles/" + edge[1], null, token);
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
    String ab = "/api/roles/" + a.id() + "/inherited-roles/" + b.id();
    expect(204, "PUT", ab, null, token);
    expect(204, "PUT", ab, null, token);
    assertEquals(
        1, expect(200, "GET", "/api/roles/" + a.id() + "/inherited-roles", null, token).size());
    expect(204, "PUT", "/api/roles/" + b.id() + "/inherited-roles/" + c.id(), null, token);
    expect(409, "PUT", "/api/roles/" + c.id() + "/inherited-roles/" + a.id(), null, token);
    expect(409, "PUT", "/api/roles/" + a.id() + "/inherited-roles/" + a.id(), null, token);
    expect(409, "DELETE", "/api/roles/" + b.id(), null, token);
    var other = store.createRole(store.user(userId("other")).departmentId(), "foreign");
    expect(403, "PUT", "/api/roles/" + a.id() + "/inherited-roles/" + other.id(), null, token);
    expect(403, "DELETE", "/api/roles/" + a.id() + "/inherited-roles/" + other.id(), null, token);
    expect(404, "PUT", "/api/roles/" + a.id() + "/inherited-roles/9999999", null, token);
    expect(400, "PUT", "/api/roles/" + a.id() + "/inherited-roles/0", null, token);
    expect(
        403,
        "GET",
        "/api/roles/" + a.id() + "/inherited-roles",
        null,
        login("viewer", "course-project-management"));
    expect(204, "DELETE", ab, null, token);
    expect(204, "DELETE", ab, null, token);
  }

  @Test
  void serializesConcurrentOppositeInheritanceWrites() throws Exception {
    Long dept = store.user(userId("admin")).departmentId();
    var a = store.createRole(dept, "concurrent-a");
    var b = store.createRole(dept, "concurrent-b");
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var first =
          pool.submit(
              () -> {
                start.await();
                return call(
                        "PUT", "/api/roles/" + a.id() + "/inherited-roles/" + b.id(), null, token)
                    .statusCode();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return call(
                        "PUT", "/api/roles/" + b.id() + "/inherited-roles/" + a.id(), null, token)
                    .statusCode();
              });
      start.countDown();
      var statuses = java.util.List.of(first.get(), second.get());
      assertTrue(statuses.contains(204), statuses.toString());
      assertTrue(statuses.contains(409), statuses.toString());
    }
  }
}
