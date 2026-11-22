package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.edu.xmu.rbac.service.port.ManagementStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

abstract class ContractFixture {
  static final String LOGIN = "/auth/login";
  static final String IDENTITY_ME = "/auth/me";
  static final String IDENTITY_DEPARTMENTS = "/auth/departments";
  static final String IDENTITY_USERS = "/auth/users/";
  static final String API_PERMISSIONS = "/auth/api-permissions/";

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
  @PersistenceContext EntityManager entities;
  @Autowired PlatformTransactionManager transactions;
  @Autowired DataSource dataSource;
  @Autowired ManagementStore store;
  final JsonMapper json = JsonMapper.builder().build();
  final HttpClient client = HttpClient.newHttpClient();
  String token;

  @BeforeEach
  void seed() {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              entities
                  .createQuery("update DepartmentEntity d set d.parentId = null")
                  .executeUpdate();
              for (String entity :
                  new String[] {
                    "LoginSessionEntity",
                    "RoleInheritanceEntity",
                    "UserRoleEntity",
                    "RolePermissionEntity",
                    "PermissionEntity",
                    "SystemEntity",
                    "UserEntity",
                    "RoleEntity",
                    "DepartmentEntity"
                  }) {
                entities.createQuery("delete from " + entity).executeUpdate();
              }
            });
    new ResourceDatabasePopulator(new ClassPathResource("demo.sql")).execute(dataSource);
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
    return store.userByUsername(username).id();
  }

  long roleId(String name) {
    return entities
        .createQuery("select r.id from RoleEntity r where r.name = :name", Long.class)
        .setParameter("name", name)
        .getSingleResult();
  }

  long systemId(String key) {
    return entities
        .createQuery("select s.id from SystemEntity s where s.systemKey = :key", Long.class)
        .setParameter("key", key)
        .getSingleResult();
  }

  long permissionId(String system, String code) {
    return entities
        .createQuery(
            "select p.id from PermissionEntity p where p.systemId = :system and p.code = :code",
            Long.class)
        .setParameter("system", systemId(system))
        .setParameter("code", code)
        .getSingleResult();
  }

  void insert(String sql) {
    var resource = new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
    var populator = new ResourceDatabasePopulator(resource);
    populator.setSqlScriptEncoding("UTF-8");
    populator.execute(dataSource);
  }

  long departmentId(String name) {
    return entities
        .createQuery("select d.id from DepartmentEntity d where d.name = :name", Long.class)
        .setParameter("name", name)
        .getSingleResult();
  }

  void grant(int status, long role, long permission) {
    expect(
        status,
        "POST",
        "/api/roles/" + role + "/permissions",
        "{\"permissionId\":" + permission + "}",
        token);
  }

  void inherit(int status, long senior, long junior) {
    expect(
        status,
        "POST",
        "/api/roles/" + senior + "/inherited-roles",
        "{\"juniorId\":" + junior + "}",
        token);
  }
}
