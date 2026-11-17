package cn.edu.xmu.rbac.infrastructure.bootstrap;

import cn.edu.xmu.rbac.service.auth.UiPermissions;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 初始化两个接入系统的演示账号及授权目录. */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {
  private final ManagementStore store;
  private final JdbcTemplate jdbc;
  private final String phone;

  /** 装配DemoData所需依赖. */
  public DemoData(
      ManagementStore store, JdbcTemplate jdbc, @Value("${app.demo.phone}") String phone) {
    this.store = store;
    this.jdbc = jdbc;
    this.phone = phone;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class) > 0) {
      return;
    }
    if (!phone.matches("1[3-9][0-9]{9}")) {
      throw new IllegalArgumentException("app.demo.phone requires a valid demo phone");
    }
    var dept = store.createDepartment(new Data.CreateDepartment("教学部", null));
    var other = store.createDepartment(new Data.CreateDepartment("教务部", dept.id()));
    var admin = store.createUser(dept.id(), new Data.CreateUser("admin", "演示管理员", phone));
    var reviewer = store.createUser(dept.id(), new Data.CreateUser("reviewer", "审核教师", phone));
    var viewer = store.createUser(dept.id(), new Data.CreateUser("viewer", "普通教师", phone));
    store.createUser(other.id(), new Data.CreateUser("other", "其他部门用户", phone));
    var adminRole = store.createRole(dept.id(), "admin");
    var reviewRole = store.createRole(dept.id(), "reviewer");
    var viewRole = store.createRole(dept.id(), "viewer");
    store.assignRole(admin.id(), adminRole.id());
    store.assignRole(reviewer.id(), reviewRole.id());
    store.assignRole(viewer.id(), viewRole.id());
    var rbac = store.createSystem(new Data.CreateSystem("rbac", "统一权限管理后台"));
    for (String code :
        List.of(
            "login",
            "user.view",
            "user.create",
            "user.edit",
            "user.delete",
            "user.role.assign",
            "department.manage",
            "system.manage",
            "permission.manage",
            "role.manage")) {
      var p = store.createPermission(rbac.id(), new Data.CreatePermission(code, code));
      store.grantPermission(adminRole.id(), p.id());
    }
    for (String code : UiPermissions.PLATFORM) {
      var p = store.createPermission(rbac.id(), new Data.CreatePermission(code, code));
      store.grantPermission(adminRole.id(), p.id());
    }
    var course = store.createSystem(new Data.CreateSystem("course-project-management", "课程管理系统"));
    for (String code : List.of("login", "TC", "TE", "TD", "CV", "CR", "UL")) {
      var p = store.createPermission(course.id(), new Data.CreatePermission(code, code));
      store.grantPermission(adminRole.id(), p.id());
      store.grantPermission(reviewRole.id(), p.id());
      if (code.equals("login") || code.equals("CV")) {
        store.grantPermission(viewRole.id(), p.id());
      }
    }
    for (String code : UiPermissions.COURSE) {
      var p = store.createPermission(course.id(), new Data.CreatePermission(code, code));
      store.grantPermission(adminRole.id(), p.id());
      store.grantPermission(reviewRole.id(), p.id());
      if (code.equals("MCO")) {
        store.grantPermission(viewRole.id(), p.id());
      }
    }
  }
}
