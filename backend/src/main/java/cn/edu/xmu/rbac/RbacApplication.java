package cn.edu.xmu.rbac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 统一权限管理平台启动入口. */
@SpringBootApplication
public class RbacApplication {
  /**
   * 启动 Spring Boot 权限应用.
   *
   * @param args 应用启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(RbacApplication.class, args);
  }
}
