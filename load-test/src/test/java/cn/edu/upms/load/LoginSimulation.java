package cn.edu.upms.load;

import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/** ACC-P-001：不同账号在六十秒内均匀发起完整登录，并保存后续鉴权需要的后端令牌. */
public class LoginSimulation extends Simulation {
  private BufferedWriter tokens;
  private final int users = Integer.getInteger("load.users", 10000);
  private final int seconds = Integer.getInteger("load.seconds", 60);

  /** 整组耗时覆盖跳转、校验、兑换、签名验证及 UserInfo；不把单个请求冒充完整登录. */
  public LoginSimulation() {
    var scenario =
        scenario("完整登录峰值")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/accounts.csv").toString()).queue())
            .group("完整登录")
            .on(LoadSupport.login())
            .exitHereIfFailed()
            .exec(
                session -> {
                  try {
                    // 多个虚拟用户同时完成登录，单个 Writer 需串行写，避免 token CSV 行交叉。
                    // 这段只保护压测输入文件，不参与平台授权码并发控制。
                    synchronized (this) {
                      tokens.write(
                          session.getString("employeeId")
                              + ","
                              + session.getString("token")
                              + ","
                              + session.getString("expectedAllowed")
                              + "\n");
                    }
                    return session;
                  } catch (Exception failure) {
                    return session.markAsFailed();
                  }
                });
    setUp(scenario.injectOpen(rampUsers(users).during(Duration.ofSeconds(seconds))))
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            details("完整登录").responseTime().percentile3().lte(3000),
            details("完整登录").failedRequests().percent().lte(1.0),
            global().failedRequests().percent().lte(1.0));
  }

  /** 每次实验重建临时令牌输入，避免误用上次实验或不同签名节点的令牌. */
  @Override
  public void before() {
    try {
      tokens =
          Files.newBufferedWriter(
              LoadSupport.ROOT.resolve(".runtime/load/tokens.csv"), StandardCharsets.UTF_8);
      tokens.write("employeeId,token,expectedAllowed\n");
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** 写完输入后关闭文件；该文件只保存在被 Git 忽略的运行目录. */
  @Override
  public void after() {
    try {
      tokens.close();
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
