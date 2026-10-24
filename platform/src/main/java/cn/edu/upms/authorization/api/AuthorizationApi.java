package cn.edu.upms.authorization.api;

import cn.edu.upms.catalog.api.CatalogApi;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 授权模块的公开契约：保存角色分配、解释当前有效权限并作出业务判断.
 *
 * <p>Controller 先通过身份模块取得可信员工和系统编号，再调用这里的能力。编号本身不能证明调用者已登录；管理写入还会在事务内重新校验操作者范围。返回的 record
 * 是事实副本，调用方不能通过修改它们改变数据库，也不能跨模块取得 JPA 实体.
 *
 * <p>建议阅读顺序：{@link #change} 理解授权写入，再看 {@link #effective} 与 {@link #decide} 理解为什么"保存过角色"仍可能得到拒绝.
 */
public interface AuthorizationApi {
  /**
   * 一名员工在一个系统中的角色分配事实.
   *
   * <p>从未创建分配时返回虚拟事实；撤权后保留真实记录，后来的请求仍须核对旧版本.
   *
   * @param employeeId 被授权员工编号，与操作者编号不同
   * @param systemId 目标业务系统编号
   * @param roleId 当前角色编号；从未授权或已撤权时为 {@code null}
   * @param revoked 是否已撤权；仅为 {@code false} 仍不足以证明当前可用
   * @param version 数据版本；从未创建为 {@code -1}，后续写入须回传读取到的版本
   * @param changedAt 最近一次授权或撤权时间；从未创建时为 {@code null}
   */
  record Assignment(
      Long employeeId,
      Long systemId,
      Long roleId,
      boolean revoked,
      long version,
      Instant changedAt) {}

  /**
   * 将分配事实与当前状态综合后得到的有效权限.
   *
   * <p>assignment 解释"保存过什么"，permissions 解释"现在能做什么"；两者可能不同.
   *
   * @param assignment 当前或虚拟分配事实
   * @param role 关联角色；没有可读取角色时为 {@code null}
   * @param permissions 已排除停用项的稳定权限标识集合，拒绝时为空
   * @param reason 有效或拒绝的原因代码，供界面解释和审计使用
   */
  record Effective(
      Assignment assignment, CatalogApi.Role role, Set<String> permissions, String reason) {}

  /** 鉴权结果；业务拒绝返回明确原因，技术故障则抛出不可用异常. */
  record Decision(boolean allowed, String reason) {}

  /** 获取可信模块需要的当前分配事实. */
  Assignment assignment(long employeeId, long systemId);

  /** 读取可信模块需要的有效事实；管理入口须先检查员工范围. */
  Effective effective(long employeeId, long systemId);

  /**
   * 判断已核验身份能否执行一个功能动作.
   *
   * <p>普通无权返回 {@code allowed=false}；审计或事实读取故障通过异常报告，调用方须拒绝操作。本方法不会核验 Bearer
   * 令牌，也不会替业务系统判断某条记录是否属于"本人".
   *
   * @param employeeId 从已验证令牌取得的员工编号
   * @param systemId 与令牌绑定且已校验的目标系统编号
   * @param permissionKey 外部目录登记的稳定功能动作标识
   * @return 本次判断及原因；允许结果不能由客户端长期缓存
   */
  Decision decide(long employeeId, long systemId, String permissionKey);

  /**
   * 在一个事务内分配、替换或撤销员工角色，并写入成功审计.
   *
   * <p>每员工每系统最多一条分配；换角覆盖旧角色。锁用于安排并发执行顺序，版本用于发现用户基于旧页面提交的意图，两种检查解决不同问题。失败时分配与成功审计共同回滚.
   *
   * @param actorId 从后台会话取得的操作者编号，不能信任前端自报
   * @param employeeId 被管理的员工编号
   * @param systemId 目标系统编号
   * @param roleId 要替换成的部门角色；{@code null} 表示撤权
   * @param expectedVersion 页面读取的分配版本；首次授权使用 {@code -1}
   * @return 已刷新数据库版本的分配事实
   */
  Assignment change(
      long actorId, long employeeId, long systemId, Long roleId, long expectedVersion);

  /**
   * 在员工停用事务内撤销该员工全部系统的角色.
   *
   * <p>必须由已开启事务、已取得治理锁和员工锁的协调者调用。这里参加同一事务，不能单独提交，否则会出现员工停用失败但角色已撤销的中间结果。统一会话由协调者在提交后失效.
   */
  void revokeAll(long actorId, long employeeId);

  /** 校验人工撤权已完成并越过最大生效窗口，供调动用例调用. */
  void requireTransferReady(long employeeId);

  /** 查询可维护员工全部系统的有效状态，跨部门请求直接拒绝. */
  List<Effective> employeePermissions(long actorId, long employeeId);

  /** 列出角色变更影响的员工；调用方只得到管理范围内的身份编号. */
  List<Long> affected(long actorId, long roleId);
}
