package cn.edu.upms.authorization.api;

import cn.edu.upms.catalog.api.CatalogApi;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 授权写入、有效事实与纯权限判断的公开能力，不承担 OIDC 令牌验证. */
public interface AuthorizationApi {
  /** 分配事实；不存在时 version=-1，撤销仍保留版本以阻止旧请求覆盖. */
  record Assignment(
      Long employeeId,
      Long systemId,
      Long roleId,
      boolean revoked,
      long version,
      Instant changedAt) {}

  /** 当前有效权限快照；无角色、停用或范围取消时 permissions 为空. */
  record Effective(
      Assignment assignment, CatalogApi.Role role, Set<String> permissions, String reason) {}

  /** 鉴权结果；业务拒绝返回明确原因，技术故障则抛出不可用异常. */
  record Decision(boolean allowed, String reason) {}

  /** 获取可信模块需要的当前分配事实. */
  Assignment assignment(long employeeId, long systemId);

  /** 读取可信模块需要的有效事实；管理入口须先检查员工范围. */
  Effective effective(long employeeId, long systemId);

  /** 已由身份模块核验的员工与目标系统进行逐次鉴权. */
  Decision decide(long employeeId, long systemId, String permissionKey);

  /** 分配或换角；roleId=null 为撤权，expectedVersion 禁止静默覆盖. */
  Assignment change(
      long actorId, long employeeId, long systemId, Long roleId, long expectedVersion);

  /** 在员工停用协调事务内撤销所有系统分配；协调者先锁定员工. */
  void revokeAll(long actorId, long employeeId);

  /** 校验人工撤权已完成并越过最大生效窗口，供调动用例调用. */
  void requireTransferReady(long employeeId);

  /** 查询可维护员工全部系统的有效状态，跨部门请求直接拒绝. */
  List<Effective> employeePermissions(long actorId, long employeeId);

  /** 列出角色变更影响的员工；调用方只得到管理范围内的身份编号. */
  List<Long> affected(long actorId, long roleId);
}
