package cn.edu.upms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一权限平台启动入口，Spring Boot 从本包向下扫描配置、Controller 和 Service.
 *
 * <p>业务仍按六个模块组织，单个启动类不代表所有职责混在一起。EnableScheduling 启用会话过期清理和审计恢复探测；请求身份由安全上下文逐请求提供，不在启动类保存.
 */
@SpringBootApplication
@EnableScheduling
public class UpmsApplication {
  /** 启动平台，不在入口类中维护员工或授权状态. */
  public static void main(String[] args) {
    SpringApplication.run(UpmsApplication.class, args);
  }
}
