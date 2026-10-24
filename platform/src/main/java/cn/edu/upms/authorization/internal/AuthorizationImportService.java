package cn.edu.upms.authorization.internal;

import cn.edu.upms.authorization.api.AuthorizationImportApi;
import cn.edu.upms.shared.BusinessException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 只为空压测分配表批量写入已经核对归属的夹具，不是运行期授权捷径.
 *
 * <p>上层 LoadInitializer 负责专用库限制、角色创建及归属核对；这里要求现有分配为空，参加上层事务并只写本模块表。运行期必须使用
 * AuthorizationApi.change，不能跳过范围、版本、锁与审计检查来调用本导入器.
 */
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
