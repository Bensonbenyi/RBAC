package cn.edu.upms.integration.api;

import java.util.List;
import java.util.Set;

/**
 * 登记外部系统、管理接入凭据和部门可用范围.
 *
 * <p>"部门可用该系统"只是授权的必要条件，仍须给员工分配该系统的部门角色。 OIDC 登录客户端与目录上报客户端使用不同身份和凭据，不能相互替代。
 * 本模块保存登记事实，身份模块再把这些事实适配成安全框架的客户端配置.
 */
public interface IntegrationApi {
  /**
   * 仅供可信模块使用的完整系统登记事实.
   *
   * <p>clientSecret 与 directorySecret 的名称来自框架约定，内容是存储的凭据哈希。后台响应必须显式挑选安全字段，禁止直接序列化整个 SystemFact。
   * directoryRevision 是外部目录内容版本，version 是 JPA 并发版本，二者不能互换.
   */
  record SystemFact(
      Long id,
      String key,
      String name,
      boolean active,
      String clientSecret,
      String directorySecret,
      String redirectUri,
      String logoutRedirectUri,
      long directoryRevision,
      long version,
      Set<Long> departments) {}

  /** 系统登记资料；客户端标识建立后不能修改. */
  record SystemChange(
      Long id,
      String key,
      String name,
      boolean active,
      String redirectUri,
      String logoutRedirectUri,
      long version) {}

  /** 仅在重新签发响应中展示一次的凭据，不保存明文、不进入审计. */
  record Credentials(
      String clientId,
      String clientSecret,
      String directoryClientId,
      String directorySecret,
      long version) {}

  /** 读取系统；调用方必须另行执行员工或管理员范围校验. */
  SystemFact system(long id);

  /** 认证适配器按客户端标识读取登记事实. */
  SystemFact byKey(String key);

  /** 认证适配器读取所有客户端登记；不暴露为无认证的管理接口. */
  List<SystemFact> registered();

  /** 查询管理员可见系统. */
  List<SystemFact> systems(long actorId);

  /** 最高管理员登记或更新系统；新增系统的凭据由服务端生成. */
  SystemFact save(long actorId, SystemChange change);

  /** 最高管理员显式重新签发独立凭据；旧凭据不能再发起兑换或目录取令牌. */
  Credentials rotateCredentials(long actorId, long systemId, long version);

  /**
   * 将系统可用部门替换为用户明确提交的集合.
   *
   * <p>空集合表示取消全部部门范围；不是给旧集合追加几个部门。关联取消后旧角色分配记录仍可保留，但有效权限判断会拒绝使用。可选权限快照仍受整体最多三秒生效要求约束.
   *
   * @param actorId 最高管理员编号
   * @param systemId 要调整的系统编号
   * @param departments 替换后的完整部门编号集合
   * @param version 当前系统的 JPA 版本
   * @return 更新后的系统事实
   */
  SystemFact bind(long actorId, long systemId, Set<Long> departments, long version);

  /** 返回部门是否可用该系统；停用系统也返回 false. */
  boolean available(long systemId, Long departmentId);

  /** 参加上层事务，锁定接入事实，供换角和目录变更共同使用. */
  SystemFact lock(long systemId);

  /** 在调用者事务内推进目录版本，禁止旧报文覆盖新目录. */
  void advanceDirectory(long systemId, long revision);
}
