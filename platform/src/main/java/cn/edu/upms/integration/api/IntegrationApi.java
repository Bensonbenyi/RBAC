package cn.edu.upms.integration.api;

import java.util.List;
import java.util.Set;

/** 系统接入能力；客户端凭据与部门关联的持久化细节保留在模块内. */
public interface IntegrationApi {
  /** 系统事实；凭据仅供认证适配器读取，不作为后台响应直接返回. */
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

  /** 原子替换系统可用部门；取消关联立即影响后续鉴权. */
  SystemFact bind(long actorId, long systemId, Set<Long> departments, long version);

  /** 返回部门是否可用该系统；停用系统也返回 false. */
  boolean available(long systemId, Long departmentId);

  /** 参加上层事务，锁定接入事实，供换角和目录变更共同使用. */
  SystemFact lock(long systemId);

  /** 在调用者事务内推进目录版本，禁止旧报文覆盖新目录. */
  void advanceDirectory(long systemId, long revision);
}
