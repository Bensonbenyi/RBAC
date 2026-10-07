package cn.edu.upms.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 模拟业务系统入口；OA 和知识库使用两个独立进程及独立会话. */
@SpringBootApplication
public class DemoApplication {
  /** 根据运行配置启动一个模拟业务系统. */
  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
