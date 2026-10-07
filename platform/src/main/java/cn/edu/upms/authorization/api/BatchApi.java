package cn.edu.upms.authorization.api;

import java.time.Instant;
import java.util.List;

/** 可查询、逐项提交和重试的批量任务能力；越范围必须整批拒绝. */
public interface BatchApi {
  /** 员工期望分配版本；重试需由操作者确认最新版本. */
  record Target(long employeeId, long expectedVersion) {}

  /** 一次批量意图；requestKey 防止重复提交创建新任务. */
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

  /** 重试明确失败的选项；期望版本必须由用户重新确认. */
  Job retry(long actorId, long jobId, List<Target> targets);
}
