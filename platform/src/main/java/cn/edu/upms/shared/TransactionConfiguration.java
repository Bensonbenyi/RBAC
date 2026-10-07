package cn.edu.upms.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 提供显式事务模板，供导入与批量用例定义提交边界. */
@Configuration
public class TransactionConfiguration {
  /** 使用与 JPA 一致的事务管理器，确保审计与业务变更一起提交. */
  @Bean
  public TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
    TransactionTemplate template = new TransactionTemplate(manager);
    template.setIsolationLevelName("ISOLATION_READ_COMMITTED");
    return template;
  }
}
