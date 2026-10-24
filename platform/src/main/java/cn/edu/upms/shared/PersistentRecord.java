package cn.edu.upms.shared;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * 业务实体共用的数据库主键与 JPA 乐观锁版本映射.
 *
 * <p>@MappedSuperclass 把字段映射继承给各实体，不创建一张"父类表"。@Id 放在字段上，因此 JPA 直接读写字段；不必为所有属性创建 setter。构造出的新实体 id 为
 * null，数据库插入后生成编号，业务模块对外只返回事实 record.
 *
 * <p>@Version 让更新包含旧版本条件，成功修改后递增。请求的 expectedVersion 另用于确认用户看到的旧状态；悲观锁安排写入顺序，唯一键防止重复行，三者不能相互替代.
 */
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
