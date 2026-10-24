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
    // 用队列逐个消耗账号输入，保证峰值登录使用不同员工身份。
    var scenario =
        scenario("完整登录峰值")
            .feed(csv(LoadSupport.ROOT.resolve(".runtime/load/accounts.csv").toString()).queue())
            // 整段登录包在同一测量组内，响应时间覆盖完整协议链。
            .group("完整登录")
            .on(LoadSupport.login())
            // 只有完整登录成功的用户才把访问令牌保存给后续鉴权实验。
            .exitHereIfFailed()
            .exec(
                session -> {
                  try {
                    // 多个虚拟用户同时完成登录，单个 Writer 需串行写，避免 token CSV 行交叉。
                    // 这段只保护压测输入文件，不参与平台授权码并发控制。
                    synchronized (this) {
                      tokens.write(
                          // 写入身份、令牌和预期结果，后续鉴权保持三者对应。
                          session.getString("employeeId")
                              + ","
                              + session.getString("token")
                              + ","
                              // 预期值仍只用于正确性断言，令牌文件保存在忽略的运行目录。
                              + session.getString("expectedAllowed")
                              + "\n");
                    }
                    // 输入保存完成后保留用户成功状态。
                    return session;
                  } catch (Exception failure) {
                    // 文件写入失败也使准备过程失败，不能假装已生成可复现实验输入。
                    return session.markAsFailed();
                  }
                });
    // 默认在六十秒内均匀注入一万个不同用户，时间与人数可用实验参数调整。
    setUp(scenario.injectOpen(rampUsers(users).during(Duration.ofSeconds(seconds))))
        // 应用共用 HTTP 协议配置；共享连接不会共享各虚拟用户身份。
        .protocols(LoadSupport.PROTOCOL)
        .assertions(
            // percentile3 在本项目配置为 P95，完整登录目标为三秒。
            details("完整登录").responseTime().percentile3().lte(3000),
            // 同时限制完整链和所有 HTTP 请求失败率，不能只看单个快速端点。
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
