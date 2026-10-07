package cn.edu.upms.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 权限目录存储，只允许目录模块使用. */
interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
  List<PermissionEntity> findBySystemId(long systemId);
}
