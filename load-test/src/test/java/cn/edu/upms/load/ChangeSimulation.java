package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** ACC-P-005：变更探针同时运行时持续施加一千次每秒鉴权，排除专用被变更员工. */
public class ChangeSimulation extends Simulation {
  /** 背景负载中不容忍错误判断. */
  @Override
  public void after() {
    LoadSupport.verifyDecisions();
  }

  /** 原始判断与提交后期限由 verify-changes.mjs 记录，背景负载仍独立检查正确性. */
  public ChangeSimulation() {
    setUp(
            scenario("变更期间持续鉴权")
                .feed(
                    csv(LoadSupport.ROOT.resolve(".runtime/load/change-tokens.csv").toString())
                        .circular())
                .exec(LoadSupport.checkPermission())
                .injectOpen(constantUsersPerSec(1000).during(Duration.ofSeconds(90))))
        .protocols(LoadSupport.PROTOCOL)
        .assertions(global().failedRequests().percent().lte(1.0));
  }
}
