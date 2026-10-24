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
    // 先将该系统稳定 key 映射为刚导入的数据库编号，模板内部保存编号引用。
    Map<String, Long> ids =
        permissions.findBySystemId(systemId).stream()
            .collect(Collectors.toMap(p -> p.permissionKey, PermissionEntity::getId));
    // 逐个全局模板处理，不在这里给任何员工建立分配。
    grants.forEach(
        (name, keys) -> {
          // 每个模板独立一行，绑定所属系统并显式标记 template。
          RoleEntity role = new RoleEntity();
          role.systemId = systemId;
          role.template = true;
          // 保留来源模板名；部门保持 null，后续需要复制为部门角色才能分配。
          role.name = name;
          for (String key : keys) {
            // 逐 key 查询目录编号，缺失说明输入与目录不一致，不能静默忽略。
            Long id = ids.get(key);
            if (id == null) {
              throw new IllegalStateException("初始模板引用未知动作");
            }
            // 校验通过的编号加入该模板选择，重复项由集合去重。
            role.permissions.add(id);
          }
          // 模板与权限集合加入当前事务，外层失败会一起回滚。
          roles.save(role);
        });
    // 在方法结束前刷新所有模板，提前发现关联约束错误。
    roles.flush();
  }
}
