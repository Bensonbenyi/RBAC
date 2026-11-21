package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 以指定角色或用户的直接角色为起点，沿继承边求有效角色. */
@Repository
public class JdbcEffectiveAccessStore implements EffectiveAccessStore {
  // UNION DISTINCT 让同一角色经多条路径到达时只进入闭包一次。
  private static final String EFFECTIVE_ROLES =
      """
      WITH RECURSIVE effective(id) AS (
        %s
        UNION DISTINCT
        SELECT h.junior_role_id FROM role_inheritance h
        JOIN effective e ON h.senior_role_id=e.id
      )
      """;
  private static final String FROM_ROLE =
      EFFECTIVE_ROLES.formatted("SELECT id FROM `role` WHERE id=?");
  private static final String FROM_USER =
      EFFECTIVE_ROLES.formatted("SELECT role_id FROM user_role WHERE user_id=?");
  private final JdbcTemplate jdbc;

  public JdbcEffectiveAccessStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Data.Permission> effectiveRolePermissions(Long roleId) {
    return jdbc.query(
        FROM_ROLE
            + """
              SELECT DISTINCT p.* FROM effective e
              JOIN role_permission rp ON rp.role_id=e.id
              JOIN permission p ON p.id=rp.permission_id
              ORDER BY p.id
              """,
        (row, index) ->
            new Data.Permission(
                row.getLong("id"),
                row.getLong("system_id"),
                row.getString("code"),
                row.getString("name")),
        roleId);
  }

  @Override
  public List<String> permissionsForUser(Long userId, String systemKey) {
    return jdbc.queryForList(
        FROM_USER
            + """
              SELECT DISTINCT p.code FROM effective e
              JOIN role_permission rp ON rp.role_id=e.id
              JOIN permission p ON p.id=rp.permission_id
              JOIN business_system s ON s.id=p.system_id
              WHERE s.system_key=?
              ORDER BY p.code
              """,
        String.class,
        userId,
        systemKey);
  }

  @Override
  public List<String> rolesForUser(Long userId) {
    return jdbc.queryForList(
        FROM_USER + "SELECT r.name FROM `role` r JOIN effective e ON e.id=r.id ORDER BY r.id",
        String.class,
        userId);
  }
}
