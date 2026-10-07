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
    int seconds = Integer.getInteger("load.seconds", 300);
    var checks =
        scenario("混合负载鉴权")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/tokens.csv").toString()).circular())
            .exec(LoadSupport.checkPermission());
    var admins =
        scenario("混合负载管理员")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/admins.csv").toString()).queue())
            .exec(AdminSimulation.operations(seconds));
    setUp(
            checks.injectOpen(
                constantUsersPerSec(Integer.getInteger("load.rate", 1000))
                    .during(Duration.ofSeconds(seconds))),
            admins.injectOpen(atOnceUsers(Integer.getInteger("load.admins", 100))))
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            details("实时鉴权").responseTime().percentile3().lte(300),
            details("管理员常用查询").responseTime().percentile3().lte(3000),
            details("管理员员工详情").responseTime().percentile3().lte(3000),
            details("管理员单项资料维护").responseTime().percentile3().lte(3000),
            global().failedRequests().percent().lte(1.0));
  }
}
