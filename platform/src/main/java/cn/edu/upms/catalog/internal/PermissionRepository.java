package cn.edu.upms.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 目录存储，按系统读取时包含有效与停用项.
 *
 * <p>角色编辑需要解释历史选择，目录重报需要比较完整内容，因此不能在此统一只过滤 active。鉴权读取会另与当前有效目录取交集.
 */
interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
  List<PermissionEntity> findBySystemId(long systemId);
}
