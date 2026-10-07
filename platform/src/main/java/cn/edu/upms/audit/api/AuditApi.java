package cn.edu.upms.audit.api;

import cn.edu.upms.shared.PageResult;
import java.time.Instant;
import java.util.Map;

/** 审计模块公开能力；调用方传入已校验的范围，不传持久化实体. */
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

  /** 同步保存事件；参加调用者事务，失败使管理变更整体回滚. */
  void record(Event event);

  /** 检查故障暂停状态；恢复后仍需重新执行权限判断. */
  void requireAvailable();

  /** 按已验证范围和筛选条件分页查询；null 范围只由最高管理员入口使用. */
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
