package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** ACC-P-003：一百个独立管理员持续查询、查看详情及提交单项资料维护. */
public class AdminSimulation extends Simulation {
  static ChainBuilder operations(int seconds) {
    return exec(LoadSupport.login())
        .exitHereIfFailed()
        .exec(http("刷新管理 CSRF").get("/api/csrf").check(jsonPath("$.token").saveAs("csrf")))
        .during(Duration.ofSeconds(seconds))
        .on(
            exec(http("管理员常用查询")
                    .get("/api/admin/employees")
                    .queryParam("size", "20")
                    .check(status().is(200)))
                .exec(
                    http("管理员员工详情")
                        .get("/api/admin/employees/#{employeeId}")
                        .check(
                            status().is(200),
                            jsonPath("$.version").saveAs("version"),
                            jsonPath("$.companyId").saveAs("companyId"),
                            jsonPath("$.departmentId").optional().saveAs("departmentId"),
                            jsonPath("$.name").saveAs("name"),
                            jsonPath("$.positionName").saveAs("position")))
                .exec(
                    http("管理员单项资料维护")
                        .post("/api/admin/employees")
                        .asJson()
                        .header("X-CSRF-TOKEN", "#{csrf}")
                        .body(
                            StringBody(
                                session ->
                                    "{\"id\":"
                                        + session.getString("employeeId")
                                        + ",\"username\":\""
                                        + session.getString("username")
                                        + "\",\"phone\":\""
                                        + session.getString("phone")
                                        + "\",\"name\":\""
                                        + session.getString("name")
                                        + "\",\"positionName\":\""
                                        + session.getString("position")
                                        + "\",\"companyId\":"
                                        + session.getString("companyId")
                                        + ",\"departmentId\":"
                                        + (session.contains("departmentId")
                                            ? session.getString("departmentId")
                                            : "null")
                                        + ",\"version\":"
                                        + session.getString("version")
                                        + "}"))
                        .check(status().is(200)))
                .pause(Duration.ofSeconds(1)));
  }

  /** 思考时间固定一秒，不用无上限循环把管理员场景改成到达率压测. */
  public AdminSimulation() {
    setUp(
            scenario("100 名管理员并发")
                .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/admins.csv").toString()).queue())
                .exec(operations(Integer.getInteger("load.seconds", 300)))
                .injectOpen(atOnceUsers(Integer.getInteger("load.admins", 100))))
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            details("管理员常用查询").responseTime().percentile3().lte(3000),
            details("管理员员工详情").responseTime().percentile3().lte(3000),
            details("管理员单项资料维护").responseTime().percentile3().lte(3000),
            global().failedRequests().percent().lte(1.0));
  }
}
