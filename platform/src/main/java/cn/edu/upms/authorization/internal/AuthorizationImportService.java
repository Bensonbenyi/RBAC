package cn.edu.upms.authorization.internal;

import cn.edu.upms.authorization.api.AuthorizationImportApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 批量装载可重复的专用压测夹具；SQL 仅访问本模块拥有的分配表. */
@Service
class AuthorizationImportService implements AuthorizationImportApi {
  private final AssignmentRepository assignments;
  private final JdbcTemplate jdbc;

  AuthorizationImportService(AssignmentRepository assignments, JdbcTemplate jdbc) {
    this.assignments = assignments;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void load(List<Row> rows) {
    if (assignments.count() != 0) {
      throw BusinessException.conflict("压测分配只能装载到空分配表");
    }
    var now = java.sql.Timestamp.from(Instant.now());
    jdbc.batchUpdate(
        "INSERT INTO role_assignment(employee_id,system_id,role_id,revoked,changed_at,version)"
            + " VALUES (?,?,?,false,?,0)",
        rows,
        500,
        (statement, row) -> {
          statement.setLong(1, row.employeeId());
          statement.setLong(2, row.systemId());
          statement.setLong(3, row.roleId());
          statement.setTimestamp(4, now);
        });
  }
}
