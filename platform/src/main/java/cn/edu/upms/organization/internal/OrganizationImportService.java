package cn.edu.upms.organization.internal;

import cn.edu.upms.organization.api.OrganizationImportApi;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用 JDBC 批处理装载组织来源事实，避免为一万名员工逐个创建完整 JPA 用例.
 *
 * <p>仅在启动初始化的外层事务中调用，MANDATORY 要求已有事务；每批 100 或 200 是提交给驱动的批量大小，不代表每批独立提交。先公司、后部门、再员工，遵守数据库外键依赖。 SQL
 * 只访问本模块拥有的表，其他模块仍通过 import api 协作.
 */
@Service
public class OrganizationImportService implements OrganizationImportApi {
  private final JdbcTemplate jdbc;

  /** 注入同一事务管理器下的数据库访问工具. */
  public OrganizationImportService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void load(
      List<CompanyRow> companies, List<DepartmentRow> departments, List<EmployeeRow> employees) {
    if (counts().get("employees") != 0 || counts().get("companies") != 0) {
      throw new IllegalStateException("基础导入只允许空组织数据集");
    }
    jdbc.batchUpdate(
        "INSERT INTO company(id,name) VALUES (?,?)",
        companies,
        100,
        (statement, row) -> {
          statement.setLong(1, row.id());
          statement.setString(2, row.name());
        });
    jdbc.batchUpdate(
        "INSERT INTO department(id,company_id,name) VALUES (?,?,?)",
        departments,
        100,
        (statement, row) -> {
          statement.setLong(1, row.id());
          statement.setLong(2, row.companyId());
          statement.setString(3, row.name());
        });
    jdbc.batchUpdate(
        """
        INSERT INTO employee(id,username,name,phone,company_id,department_id,position_name)
        VALUES (?,?,?,?,?,?,?)
        """,
        employees,
        200,
        (statement, row) -> {
          statement.setLong(1, row.id());
          statement.setString(2, row.username());
          statement.setString(3, row.name());
          statement.setString(4, row.phone());
          statement.setLong(5, row.companyId());
          statement.setObject(6, row.departmentId());
          statement.setString(7, row.position());
        });
  }

  @Override
  public Map<String, Long> counts() {
    return Map.of(
        "companies",
        count("company"),
        "departments",
        count("department"),
        "employees",
        count("employee"),
        "unconfirmedDepartments",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee WHERE department_id IS NULL", Long.class));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void demonstrationAdministrators(List<String> superUsers, List<String> departmentUsers) {
    for (String username : superUsers) {
      if (jdbc.update("UPDATE employee SET super_admin=true WHERE username=?", username) != 1) {
        throw new IllegalStateException("演示最高管理员账号不存在");
      }
    }
    for (String username : departmentUsers) {
      if (jdbc.update(
              """
          UPDATE employee SET department_admin=true
          WHERE username=? AND department_id IS NOT NULL
          """,
              username)
          != 1) {
        throw new IllegalStateException("演示部门管理员账号不存在或部门未确认");
      }
    }
  }

  private long count(String table) {
    // 表名来自本类常量，不接受请求内容。
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
