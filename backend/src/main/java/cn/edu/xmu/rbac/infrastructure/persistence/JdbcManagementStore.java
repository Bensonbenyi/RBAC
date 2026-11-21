package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 业务目录、直接授权关系和继承边的 JDBC 读写. */
@Repository
public class JdbcManagementStore implements ManagementStore {
  private final JdbcTemplate jdbc;

  public JdbcManagementStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Data.User> USER =
      (r, n) ->
          new Data.User(
              r.getLong("id"),
              r.getString("username"),
              r.getString("name"),
              r.getString("phone"),
              r.getLong("department_id"));
  private static final RowMapper<Data.Department> DEPT =
      (r, n) ->
          new Data.Department(
              r.getLong("id"), r.getString("name"), r.getObject("parent_id", Long.class));
  private static final RowMapper<Data.System> SYSTEM =
      (r, n) -> new Data.System(r.getLong("id"), r.getString("system_key"), r.getString("name"));
  private static final RowMapper<Data.Role> ROLE =
      (r, n) -> new Data.Role(r.getLong("id"), r.getString("name"), r.getLong("department_id"));
  private static final RowMapper<Data.Permission> PERMISSION =
      (r, n) ->
          new Data.Permission(
              r.getLong("id"), r.getLong("system_id"), r.getString("code"), r.getString("name"));

  private <T> T one(String sql, RowMapper<T> mapper, Object... args) {
    List<T> rows = jdbc.query(sql, mapper, args);
    if (rows.isEmpty()) {
      throw BusinessException.missing();
    }
    return rows.getFirst();
  }

  private <T> Data.Page<T> page(
      String table, String where, RowMapper<T> mapper, int page, int size, Object... params) {
    return page(table, "*", where, mapper, page, size, params);
  }

  private <T> Data.Page<T> page(
      String table,
      String columns,
      String where,
      RowMapper<T> mapper,
      int page,
      int size,
      Object... params) {
    long count =
        Objects.requireNonNull(
            jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, params));
    List<Object> args = new ArrayList<>(Arrays.asList(params));
    args.add(size);
    args.add((long) page * size);
    return new Data.Page<>(
        jdbc.query(
            "SELECT " + columns + " FROM " + table + where + " ORDER BY 1 LIMIT ? OFFSET ?",
            mapper,
            args.toArray()),
        page,
        size,
        count);
  }

  private Long insert(String sql, Object... args) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) {
            p.setObject(i + 1, args[i]);
          }
          return p;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public Data.User user(Long id) {
    return one("SELECT * FROM app_user WHERE id=?", USER, id);
  }

  public Data.User userByUsername(String username) {
    return one("SELECT * FROM app_user WHERE username=?", USER, username);
  }

  public Data.Page<Data.User> users(Long departmentId, int page, int size) {
    return page("app_user", " WHERE department_id=?", USER, page, size, departmentId);
  }

  /** 插入本部门用户，并使用数据库生成的编号读取结果. */
  public Data.User createUser(Long departmentId, Data.CreateUser c) {
    return user(
        insert(
            "INSERT INTO app_user(username,name,phone,department_id) VALUES(?,?,?,?)",
            c.username(),
            c.name(),
            c.phone(),
            departmentId));
  }

  public void updateUser(Long id, Data.Profile c) {
    jdbc.update("UPDATE app_user SET name=?,phone=? WHERE id=?", c.name(), c.phone(), id);
  }

  /** 依次删除用户会话、角色关联和账号，由调用方事务统一提交. */
  public void deleteUser(Long id) {
    jdbc.update("DELETE FROM login_session WHERE user_id=?", id);
    jdbc.update("DELETE FROM user_role WHERE user_id=?", id);
    jdbc.update("DELETE FROM app_user WHERE id=?", id);
  }

  public Data.Department department(Long id) {
    return one("SELECT * FROM department WHERE id=?", DEPT, id);
  }

  public Data.Page<Data.Department> departments(int page, int size) {
    return page("department", "", DEPT, page, size);
  }

  /** 部门编码由现有主键生成，保存名称修改时保持编码稳定. */
  public Data.Page<Data.DirectoryDepartment> directoryDepartments(
      Long parentId, String keyword, int page, int size) {
    return page(
        "department d",
        "d.id, CONCAT('D', d.id) AS code, d.name, d.parent_id,"
            + " EXISTS(SELECT 1 FROM department c WHERE c.parent_id=d.id) AS has_children",
        " WHERE d.parent_id <=> ?" + " AND (LOCATE(?,d.name)>0 OR LOCATE(?,CONCAT('D',d.id))>0)",
        (r, n) ->
            new Data.DirectoryDepartment(
                r.getLong("id"),
                r.getString("code"),
                r.getString("name"),
                r.getObject("parent_id", Long.class),
                r.getBoolean("has_children")),
        page,
        size,
        parentId,
        keyword,
        keyword);
  }

  /** 直接投影对外目录字段，避免读取联系方式. */
  public Data.Page<Data.DirectoryUser> directoryUsers(
      Long departmentId, String keyword, int page, int size) {
    return page(
        "app_user u JOIN department d ON d.id=u.department_id",
        "u.id,u.username,u.name,u.department_id,d.name AS department_name",
        " WHERE u.department_id=? AND (LOCATE(?,u.name)>0 OR LOCATE(?,u.username)>0)",
        (r, n) ->
            new Data.DirectoryUser(
                r.getString("id"),
                r.getString("username"),
                r.getString("name"),
                r.getLong("department_id"),
                r.getString("department_name")),
        page,
        size,
        departmentId,
        keyword,
        keyword);
  }

  public Data.Department createDepartment(Data.CreateDepartment c) {
    return department(
        insert("INSERT INTO department(name,parent_id) VALUES(?,?)", c.name(), c.parentId()));
  }

  public void renameDepartment(Long id, String name) {
    jdbc.update("UPDATE department SET name=? WHERE id=?", name, id);
  }

  public void deleteDepartment(Long id) {
    jdbc.update("DELETE FROM department WHERE id=?", id);
  }

  public Data.System system(Long id) {
    return one("SELECT * FROM business_system WHERE id=?", SYSTEM, id);
  }

  public Data.Page<Data.System> systems(int page, int size) {
    return page("business_system", "", SYSTEM, page, size);
  }

  /** 登记业务系统标识与名称，并读取创建结果. */
  public Data.System createSystem(Data.CreateSystem c) {
    return system(
        insert(
            "INSERT INTO business_system(system_key,name) VALUES(?,?)", c.systemKey(), c.name()));
  }

  public void renameSystem(Long id, String name) {
    jdbc.update("UPDATE business_system SET name=? WHERE id=?", name, id);
  }

  public void deleteSystem(Long id) {
    jdbc.update("DELETE FROM business_system WHERE id=?", id);
  }

  public Data.Permission permission(Long id) {
    return one("SELECT * FROM permission WHERE id=?", PERMISSION, id);
  }

  public Data.Page<Data.Permission> permissions(Long systemId, int page, int size) {
    system(systemId);
    return page("permission", " WHERE system_id=?", PERMISSION, page, size, systemId);
  }

  /** 在指定业务系统下登记权限码与名称. */
  public Data.Permission createPermission(Long systemId, Data.CreatePermission c) {
    return permission(
        insert(
            "INSERT INTO permission(system_id,code,name) VALUES(?,?,?)",
            systemId,
            c.code(),
            c.name()));
  }

  public void renamePermission(Long id, String name) {
    jdbc.update("UPDATE permission SET name=? WHERE id=?", name, id);
  }

  public void deletePermission(Long id) {
    jdbc.update("DELETE FROM permission WHERE id=?", id);
  }

  public Data.Role role(Long id) {
    return one("SELECT * FROM `role` WHERE id=?", ROLE, id);
  }

  public Data.Page<Data.Role> roles(Long departmentId, int page, int size) {
    return page("`role`", " WHERE department_id=?", ROLE, page, size, departmentId);
  }

  public Data.Role createRole(Long departmentId, String name) {
    return role(insert("INSERT INTO `role`(department_id,name) VALUES(?,?)", departmentId, name));
  }

  public void renameRole(Long id, String name) {
    jdbc.update("UPDATE `role` SET name=? WHERE id=?", name, id);
  }

  public void deleteRole(Long id) {
    jdbc.update("DELETE FROM `role` WHERE id=?", id);
  }

  public void lockHierarchy(Long departmentId) {
    jdbc.queryForObject(
        "SELECT id FROM department WHERE id=? FOR UPDATE", Long.class, departmentId);
  }

  /** 锁定部门后读取当前已提交的继承边. */
  public List<Data.Inheritance> inheritanceEdges(Long departmentId) {
    return jdbc.query(
        "SELECT h.* FROM role_inheritance h JOIN `role` r ON r.id=h.senior_role_id"
            + " WHERE r.department_id=?",
        (r, n) -> new Data.Inheritance(r.getLong("senior_role_id"), r.getLong("junior_role_id")),
        departmentId);
  }

  /** 查询直接基础角色. */
  public List<Data.Role> inheritedRoles(Long roleId) {
    return jdbc.query(
        "SELECT r.* FROM `role` r JOIN role_inheritance h ON h.junior_role_id=r.id"
            + " WHERE h.senior_role_id=? ORDER BY r.id",
        ROLE,
        roleId);
  }

  /** 幂等新增角色继承边. */
  public void inheritRole(Long seniorId, Long juniorId) {
    jdbc.update(
        "INSERT INTO role_inheritance(senior_role_id,junior_role_id) VALUES(?,?)"
            + " ON DUPLICATE KEY UPDATE junior_role_id=VALUES(junior_role_id)",
        seniorId,
        juniorId);
  }

  /** 幂等删除指定继承边. */
  public void removeInheritance(Long seniorId, Long juniorId) {
    jdbc.update(
        "DELETE FROM role_inheritance WHERE senior_role_id=? AND junior_role_id=?",
        seniorId,
        juniorId);
  }

  /** 查询用户直接分配的角色，按编号排序. */
  public List<Data.Role> userRoles(Long userId) {
    return jdbc.query(
        "SELECT r.* FROM `role` r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY"
            + " r.id",
        ROLE,
        userId);
  }

  /** 查询角色直接授予的权限，继承权限由有效权限查询计算. */
  public List<Data.Permission> rolePermissions(Long roleId) {
    return jdbc.query(
        "SELECT p.* FROM permission p JOIN role_permission rp ON rp.permission_id=p.id WHERE"
            + " rp.role_id=? ORDER BY p.id",
        PERMISSION,
        roleId);
  }

  public void assignRole(Long userId, Long roleId) {
    jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
  }

  public void revokeRole(Long userId, Long roleId) {
    jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", userId, roleId);
  }

  /** 幂等插入角色与权限的直接授权关联. */
  public void grantPermission(Long roleId, Long permissionId) {
    jdbc.update(
        "INSERT INTO role_permission(role_id,permission_id) VALUES(?,?) ON DUPLICATE KEY UPDATE"
            + " permission_id=VALUES(permission_id)",
        roleId,
        permissionId);
  }

  public void revokePermission(Long roleId, Long permissionId) {
    jdbc.update(
        "DELETE FROM role_permission WHERE role_id=? AND permission_id=?", roleId, permissionId);
  }
}
