package cn.edu.upms.catalog.internal;

import cn.edu.upms.catalog.api.CatalogImportApi;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在来源目录已经落库后，把五类初始模板的稳定权限 key 转成数据库编号.
 *
 * <p>模板只是角色选择的起点，departmentId 为空，不能直接分配给员工。未知 key 立即失败，并回滚外层基础导入事务；不能忽略未知项然后把缺权限的模板标成初始化成功.
 */
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
