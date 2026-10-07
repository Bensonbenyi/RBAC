package cn.edu.upms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 统一权限平台的单进程入口；业务规则分别归属于六个模块. */
@SpringBootApplication
@EnableScheduling
public class UpmsApplication {
  /** 启动平台，不在入口类中维护员工或授权状态. */
  public static void main(String[] args) {
    SpringApplication.run(UpmsApplication.class, args);
  }
}
