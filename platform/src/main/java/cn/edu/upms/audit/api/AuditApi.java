package cn.edu.upms.audit.api;

import cn.edu.upms.shared.PageResult;
import java.time.Instant;
import java.util.Map;

/**
 * 保存关键业务事件，并提供查询、运行计数与审计故障暂停能力.
 *
 * <p>管理成功事件参加业务事务；运行时允许只计数，拒绝需要保存事件。计数在内存中，重启归零，不能代替可持久化查询的审计日志。审计故障时暂停新的受控请求，恢复后报告缺口，不承诺补齐.
 */
public interface AuditApi {
  /** 关键事件；detail 禁止包含手机号、登录凭据或可复用令牌. */
  record Event(
      Long actorId,
      Long departmentId,
      Long previousDepartmentId,
      Long systemId,
      String eventType,
      String objectRef,
      String result,
      String detail) {}

  /** 可查询的审计事实，包含事件发生时的归属. */
  record View(
      Long id,
      Instant occurredAt,
      Long actorId,
      Long departmentId,
      Long previousDepartmentId,
      Long systemId,
      String eventType,
      String objectRef,
      String result,
      String detail) {}

  /**
   * 同步保存关键事件，并在返回前刷新到数据库以发现写入故障.
   *
   * <p>已有业务事务时加入它，保证业务状态与成功事件一起提交或回滚；没有事务时由实现开启事务。 flush 只把 SQL 发给数据库，最终提交仍由事务边界决定。失败会暂停受控服务并抛出异常.
   *
   * @param event 已经去除手机号、凭据及令牌等敏感信息的事件
   */
  void record(Event event);

  /** 检查故障暂停状态；恢复后仍需重新执行权限判断. */
  void requireAvailable();

  /**
   * 按入口已经验证的范围分页读取历史事件.
   *
   * <p>本方法不接收 actorId，因而不负责推导管理员资格；Controller 必须先把部门管理员限制在其自身部门。{@code departmentId=null}
   * 代表全局查询，只允许最高管理员入口传入。调动事件同时匹配原部门与新部门，保留历史可解释性.
   */
  PageResult<View> query(
      Long departmentId,
      String eventType,
      String result,
      String query,
      Instant from,
      Instant to,
      int page,
      int size);

  /** 统计运行结果；正常允许不逐条保存审计事件. */
  void count(String result, long elapsedNanos);

  /** 返回允许、拒绝、技术失败和耗时统计. */
  Map<String, Object> statistics();

  /** 标记审计存储失败；不得把故障期间的内存记录称为可靠补录. */
  void pause();
}
