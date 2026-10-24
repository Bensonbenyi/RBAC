package cn.edu.upms.authorization.api;

import java.time.Instant;
import java.util.List;

/**
 * 批量授权的公开契约：范围先整批检查，执行结果再逐人保存.
 *
 * <p>任务同步执行并持久化结果，不代表已接入消息队列。部分失败是可查询的业务结果，已经成功的员工不因其他员工失败而回滚。重启留下的待处理项标记为中断，须重新确认版本.
 */
public interface BatchApi {
  /** 员工期望分配版本；重试需由操作者确认最新版本. */
  record Target(long employeeId, long expectedVersion) {}

  /**
   * 一次可识别的批量操作意图.
   *
   * @param requestKey 同一操作者内的幂等标识；重复提交相同内容返回原任务，不能复用来提交新内容
   * @param systemId 整批共同作用的系统编号
   * @param roleId 整批要分配的角色；{@code null} 为撤权
   * @param targets 1 至 100 名不重复员工及各自期望版本
   */
  record Request(String requestKey, long systemId, Long roleId, List<Target> targets) {}

  /** 项目执行状态，明确成功、失败、待处理或中断. */
  record Item(long employeeId, long expectedVersion, String status, String message) {}

  /** 保存的任务及逐项结果. */
  record Job(
      long id,
      String requestKey,
      long actorId,
      long systemId,
      Long roleId,
      String status,
      Instant createdAt,
      List<Item> items) {}

  /** 先校验所有目标管理范围，再建立并执行任务；各项各自提交. */
  Job submit(long actorId, Request request);

  /** 查询范围内任务，部门管理员不能读取其它部门任务. */
  List<Job> jobs(long actorId);

  /**
   * 仅重试原任务中失败或中断的员工.
   *
   * <p>不能把已成功项重新执行，也不能顺便加入原任务以外的员工。操作者须先读取当前分配，确认新的 expectedVersion，避免重试悄悄覆盖失败后由别人作出的修改.
   */
  Job retry(long actorId, long jobId, List<Target> targets);
}
