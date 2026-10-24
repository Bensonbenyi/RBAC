package cn.edu.upms.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 提供与 JPA 共用事务管理器的显式 TransactionTemplate.
 *
 * <p>execute 内的代码共同提交，运行时异常使本次事务回滚。默认 REQUIRED 传播会加入已有事务；批量用例的外层刻意不加长事务，才能让多次 execute 成为逐项边界。
 * READ_COMMITTED 只保证不读未提交数据，不代表多次查询构成跨表原子快照.
 */
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
