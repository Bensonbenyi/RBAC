package cn.edu.upms.catalog.internal;

import cn.edu.upms.catalog.api.CatalogImportApi;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅用于基础导入，采用目录已确认动作创建模板. */
@Service
public class CatalogImportService implements CatalogImportApi {
  private final PermissionRepository permissions;
  private final RoleRepository roles;

  /** 注入目录模块自身存储. */
  public CatalogImportService(PermissionRepository permissions, RoleRepository roles) {
    this.permissions = permissions;
    this.roles = roles;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void templates(long systemId, Map<String, Set<String>> grants) {
    Map<String, Long> ids =
        permissions.findBySystemId(systemId).stream()
            .collect(Collectors.toMap(p -> p.permissionKey, PermissionEntity::getId));
    grants.forEach(
        (name, keys) -> {
          RoleEntity role = new RoleEntity();
          role.systemId = systemId;
          role.template = true;
          role.name = name;
          for (String key : keys) {
            Long id = ids.get(key);
            if (id == null) {
              throw new IllegalStateException("初始模板引用未知动作");
            }
            role.permissions.add(id);
          }
          roles.save(role);
        });
    roles.flush();
  }
}
