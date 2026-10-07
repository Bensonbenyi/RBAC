package cn.edu.upms.shared;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/** 持久化对象的标识与版本；禁止通过模块公开接口返回这个对象. */
@MappedSuperclass
public abstract class PersistentRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  public Long getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }
}
