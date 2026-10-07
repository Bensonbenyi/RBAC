package cn.edu.upms.integration.internal;

import cn.edu.upms.shared.PersistentRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 外部系统登记与客户端配置，由接入模块独占维护. */
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
