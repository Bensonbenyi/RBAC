package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.xmu.rbac.infrastructure.bootstrap.DemoData;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

abstract class ContractSuite {
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
          "user_role",
          "role_permission",
          "permission",
          "business_system",
          "app_user",
          "`role`",
          "department"
        }) jdbc.update("DELETE FROM " + table);
    new DemoData(store, jdbc, "13800000000").run(null);
    token = login("admin", "rbac");
  }

  HttpResponse<String> call(String method, String path, String body, String credential)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (credential != null) builder.header("Authorization", "Bearer " + credential);
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
  void authenticatesAndRejectsBadCredentials() {
    expect(401, "GET", "/auth/me", null, null);
    expect(
        401,
        "POST",
        "/auth/login",
        "{\"username\":\"admin\",\"password\":\"13900000000\",\"systems\":[\"rbac\"]}",
        null);
    var me = expect(200, "GET", "/auth/me", null, token);
    assertEquals("admin", me.get("username").asText());
    assertTrue(me.get("permissions").toString().contains("user.view"));
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
        "{\"username\":\"admin\",\"password\":\"13800000000\",\"systems\":[\"rbac\",\"course-project-management\"]}",
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
    expect(409, "POST", path, "{\"roleId\":\"" + roleId + "\"}", token);
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
    assertTrue(expect(200, "GET", query, null, course).get("allowed").asBoolean());
    expect(403, "GET", "/api/users", null, course);
    assertFalse(
        expect(200, "GET", "/auth/api-permissions/unknown", null, course)
            .get("allowed")
            .asBoolean());
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
  void identityLookupIsRestrictedAndUsernameIsEmployeeNumber() {
    var lookup =
        expect(
            200,
            "GET",
            "/identity/users/" + userId("viewer"),
            null,
            login("viewer", "course-project-management"));
    assertEquals("viewer", lookup.get("no").asText());
    expect(
        403,
        "GET",
        "/identity/users/" + userId("admin"),
        null,
        login("viewer", "course-project-management"));
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
}
