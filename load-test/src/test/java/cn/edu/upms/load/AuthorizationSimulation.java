package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** ACC-P-002：一万个已登录账号轮转，每秒一千次鉴权，连续五分钟. */
public class AuthorizationSimulation extends Simulation {
  /** 请求错误率不能被用于容忍权限错误. */
  @Override
  public void after() {
    LoadSupport.verifyDecisions();
  }

  /** 开放负载按到达率发起；每条结果同时检查预期允许或拒绝. */
  public AuthorizationSimulation() {
    var scenario =
        scenario("持续实时鉴权")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/tokens.csv").toString()).circular())
            .exec(LoadSupport.checkPermission());
    // 每秒到达率与同时在线用户数是不同指标；循环读取已登录账号，逐次验证当前权限。
    // percentile3 在本项目 gatling.conf 中设为 95；阈值是验收目标，执行结果另存实验报告。
    setUp(
            scenario.injectOpen(
                constantUsersPerSec(Integer.getInteger("load.rate", 1000))
                    .during(Duration.ofSeconds(Integer.getInteger("load.seconds", 300)))))
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            global().responseTime().percentile3().lte(300),
            global().failedRequests().percent().lte(1.0));
  }
}
