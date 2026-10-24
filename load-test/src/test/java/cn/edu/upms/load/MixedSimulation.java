package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** ACC-P-004：持续鉴权与独立管理员负载同时施加，分别检查各类请求. */
public class MixedSimulation extends Simulation {
  /** 即使技术失败率合格，任何错误允许或错误拒绝仍判验收失败. */
  @Override
  public void after() {
    LoadSupport.verifyDecisions();
  }

  /** 两组注入互不等待，以免顺序执行掩盖资源竞争. */
  public MixedSimulation() {
    // 两类负载使用同一持续时长，默认五分钟。
    int seconds = Integer.getInteger("load.seconds", 300);
    // 鉴权组循环复用已登录账号的令牌，测量稳定鉴权而非反复登录。
    var checks =
        scenario("混合负载鉴权")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/tokens.csv").toString()).circular())
            // 每个鉴权虚拟用户发一次查询，并校验允许或拒绝是否符合预期。
            .exec(LoadSupport.checkPermission());
    // 管理员组使用独立账号队列，与鉴权组同时竞争平台资源。
    var admins =
        scenario("混合负载管理员")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/admins.csv").toString()).queue())
            // 管理员仍执行完整登录及持续查询维护，不复用鉴权组身份。
            .exec(AdminSimulation.operations(seconds));
    // 两组同时注入：鉴权采用恒定到达率，管理员采用固定人数。
    setUp(
            checks.injectOpen(
                // 默认每秒一千个单次鉴权用户，持续给定时长，不以响应快慢改变到达率。
                constantUsersPerSec(Integer.getInteger("load.rate", 1000))
                    .during(Duration.ofSeconds(seconds))),
            // 默认一次启动一百个管理员，各自循环操作并保留思考时间。
            admins.injectOpen(atOnceUsers(Integer.getInteger("load.admins", 100))))
        // 两组共用网络协议配置，统计仍按具体请求名称分别断言。
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            // 鉴权 P95 限三百毫秒，管理请求 P95 分别限三秒。
            details("实时鉴权").responseTime().percentile3().lte(300),
            details("管理员常用查询").responseTime().percentile3().lte(3000),
            // 列表、详情和维护分别检查，避免某个慢操作被整体平均掩盖。
            details("管理员员工详情").responseTime().percentile3().lte(3000),
            details("管理员单项资料维护").responseTime().percentile3().lte(3000),
            // 总技术失败率最多百分之一；判断错误另外在 after 中执行零容忍检查。
            global().failedRequests().percent().lte(1.0));
  }
}
