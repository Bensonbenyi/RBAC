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
    // 每个管理员先独立完成标准登录，取得自己的平台会话。
    return exec(LoadSupport.login())
        // 登录失败不执行管理请求，避免使用缺失身份制造无意义后续错误。
        .exitHereIfFailed()
        // 登录后重新取得当前会话 CSRF，写接口须使用新的令牌。
        .exec(http("刷新管理 CSRF").get("/api/csrf").check(jsonPath("$.token").saveAs("csrf")))
        // 在给定时长内反复执行查询、详情和单项维护，模拟连续后台操作。
        .during(Duration.ofSeconds(seconds))
        .on(
            // 先请求常用员工列表，每页二十条，检查正常响应。
            exec(http("管理员常用查询")
                    .get("/api/admin/employees")
                    // 固定页大小使各次实验的查询构成一致。
                    .queryParam("size", "20")
                    .check(status().is(200)))
                .exec(
                    // 读取当前账号对应员工详情，取得后续提交所需的最新版本。
                    http("管理员员工详情")
                        .get("/api/admin/employees/#{employeeId}")
                        .check(
                            status().is(200),
                            // 保存版本和公司归属，避免构造绕开并发校验的写入请求。
                            jsonPath("$.version").saveAs("version"),
                            jsonPath("$.companyId").saveAs("companyId"),
                            // 待确认部门可能为空，feeder 状态里用可选字段表达。
                            jsonPath("$.departmentId").optional().saveAs("departmentId"),
                            // 保存当前展示资料，下一步原值提交以测量正常维护流程。
                            jsonPath("$.name").saveAs("name"),
                            jsonPath("$.positionName").saveAs("position")))
                .exec(
                    // 走真实 POST 用例，包含治理锁、数据库事务和成功审计。
                    http("管理员单项资料维护")
                        .post("/api/admin/employees")
                        // 设置 JSON 与 CSRF 请求头，保持浏览器后台的安全边界。
                        .asJson()
                        .header("X-CSRF-TOKEN", "#{csrf}")
                        // 请求体逐项取自本用户 CSV 和刚读取的详情；固定夹具内容可直接拼接。
                        .body(
                            StringBody(
                                session ->
                                    "{\"id\":"
                                        + session.getString("employeeId")
                                        // 用户名和手机号取自该管理员账号输入，避免改成另一员工的登录资料。
                                        + ",\"username\":\""
                                        + session.getString("username")
                                        + "\",\"phone\":\""
                                        + session.getString("phone")
                                        // 姓名与岗位取自详情读取结果，保持本次操作聚焦于保存流程。
                                        + "\",\"name\":\""
                                        + session.getString("name")
                                        + "\",\"positionName\":\""
                                        + session.getString("position")
                                        // 公司和可空部门保留原有归属，不把压测写操作变成跨部门调动。
                                        + "\",\"companyId\":"
                                        + session.getString("companyId")
                                        + ",\"departmentId\":"
                                        + (session.contains("departmentId")
                                            ? session.getString("departmentId")
                                            // 未确认部门必须生成 JSON null，不能输出字符串 null 或补造部门。
                                            : "null")
                                        // 携带刚获取的版本，冲突必须由正常业务返回，不能绕开版本要求。
                                        + ",\"version\":"
                                        + session.getString("version")
                                        + "}"))
                        // 维护操作须明确成功，失败仍计入技术或业务验收结果。
                        .check(status().is(200)))
                // 每轮停一秒模拟思考时间，固定一百用户不等于每秒一百次到达。
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
