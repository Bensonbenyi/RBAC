package cn.edu.upms.integration.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 外部业务系统登记表，独立保存登录与目录上报凭据哈希.
 *
 * <p>systemKey 是公开客户端身份且创建后固定，id 是内部数据库编号。directoryRevision 是外部报文的内容版本；managementRevision
 * 用于推动管理配置变化，继承的 version 则由 JPA 用作并发版本。不能拿外部目录版本当作系统更新请求的 expectedVersion.
 */
@Entity
@Table(name = "business_system")
class SystemEntity extends PersistentRecord {
  String systemKey;
  String name;
  boolean active = true;
  String clientSecret;
  String directorySecret;
  String redirectUri;
  String logoutRedirectUri;
  long directoryRevision;
  long managementRevision;

  protected SystemEntity() {}
}
