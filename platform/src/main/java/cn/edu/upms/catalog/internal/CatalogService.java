package cn.edu.upms.catalog.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保存权限目录、模板和部门角色，保证角色选择来自所属系统的目录.
 *
 * <p>目录上报属于系统身份操作，角色编辑属于管理员操作。两条写路径都先锁定系统，使目录停用与角色保存按顺序执行。角色逻辑删除前的员工分配检查由上层协调，本模块不反向访问授权模块
 * Repository，避免形成循环依赖.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService implements CatalogApi {
  private final PermissionRepository permissions;
  private final RoleRepository roles;
  private final IntegrationApi integration;
  private final OrganizationApi organization;
  private final AuditApi audit;

  /** 注入本模块存储及接入、管理范围、审计公开能力. */
  public CatalogService(
      PermissionRepository permissions,
      RoleRepository roles,
      IntegrationApi integration,
      OrganizationApi organization,
      AuditApi audit) {
    this.permissions = permissions;
    this.roles = roles;
    this.integration = integration;
    this.organization = organization;
    this.audit = audit;
  }

  @Override
  public Role role(long roleId) {
    return view(find(roleId));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Role lock(long roleId) {
    return view(roles.lock(roleId).orElseThrow());
  }

  @Override
  public List<Permission> permissions(long systemId) {
    integration.system(systemId);
    return permissions.findBySystemId(systemId).stream().map(CatalogService::view).toList();
  }

  @Override
  public List<Role> roles(long actorId, Long systemId, Long departmentId) {
    var actor = organization.administrator(actorId);
    if (departmentId != null) {
      organization.requireDepartmentScope(actorId, departmentId);
    }
    return roles.findAll().stream()
        .filter(r -> !r.deleted)
        .filter(r -> systemId == null || Objects.equals(r.systemId, systemId))
        .filter(
            r -> departmentId == null || r.template || Objects.equals(r.departmentId, departmentId))
        .filter(
            r ->
                actor.superAdmin()
                    || (r.template
                        ? integration.available(r.systemId, actor.departmentId())
                        : Objects.equals(r.departmentId, actor.departmentId())))
        .map(CatalogService::view)
        .toList();
  }

  @Override
  @Transactional
  public Role save(long actorId, RoleChange change) {
    // 治理锁稳定管理员范围；随后检查目标部门，模板则要求全局管理身份。
    organization.lockManagement(actorId);
    organization.requireDepartmentScope(actorId, change.departmentId());
    // 模板属于全局且部门为空，普通角色必须指定部门，两类状态不能混用。
    if (change.template() != (change.departmentId() == null)) {
      throw BusinessException.conflict("模板须为全局范围，部门角色须指定部门");
    }
    // 先锁系统，使角色编辑与同系统的目录停用按相同顺序执行。
    var system = integration.lock(change.systemId());
    // 系统必须启用；部门角色还要求该系统已向所属部门开放。
    if (!system.active()
        || (!change.template()
            && !integration.available(change.systemId(), change.departmentId()))) {
      throw BusinessException.conflict("系统已停用或未在部门可用范围内");
    }
    // 部门角色需要有效部门；全局模板没有可检查的部门编号。
    if (!change.template()) {
      organization.activeDepartment(change.departmentId());
    }
    // 新角色创建独立实体，编辑角色则取得角色行锁。
    RoleEntity entity =
        change.id() == null ? new RoleEntity() : roles.lock(change.id()).orElseThrow();
    // 锁内检查版本、删除标记与归属；不能通过编辑搬移角色或转换角色类型。
    if (change.id() != null
        && (entity.deleted
            || entity.getVersion() != change.version()
            || !Objects.equals(entity.departmentId, change.departmentId())
            || !Objects.equals(entity.systemId, change.systemId())
            || entity.template != change.template())) {
      throw BusinessException.conflict("角色已变化，不能改变其归属");
    }
    // 名称同时限制空白和长度，避免后续持久化才暴露格式错误。
    if (change.name() == null || change.name().isBlank() || change.name().length() > 120) {
      throw new BusinessException(400, "INVALID_ROLE", "角色名称不能为空或超过 120 字符");
    }
    // 复制本次完整选择集，再建立权限编号到目录事实的索引。
    Set<Long> desired = Set.copyOf(change.permissions());
    Map<Long, Permission> facts = new HashMap<>();
    // 仅读取所属系统目录，后续查不到的编号直接视为越系统选择。
    permissions(change.systemId()).forEach(p -> facts.put(p.id(), p));
    // 逐项校验请求；全部通过后才开始替换实体上的权限集合。
    for (long id : desired) {
      Permission fact = facts.get(id);
      // 已停用的历史引用可以保留；新增选择只能来自当前有效目录。
      if (fact == null || (!fact.active() && !entity.permissions.contains(id))) {
        throw BusinessException.conflict("权限不属于该系统或已经停用");
      }
    }
    // 同一系统、同一范围内名称须唯一；全局模板的空部门也在应用层检查。
    boolean duplicate =
        roles.findAll().stream()
            .anyMatch(
                r ->
                    !Objects.equals(r.getId(), change.id())
                        && Objects.equals(r.systemId, change.systemId())
                        && Objects.equals(r.departmentId, change.departmentId())
                        && r.name.equals(change.name().trim()));
    // 逻辑删除的历史名称也参与当前去重规则，避免产生含混的同名角色。
    if (duplicate) {
      throw BusinessException.conflict("相同范围内角色名称重复");
    }
    // 写入系统和部门归属，后续鉴权据此判断角色能否用于该员工。
    entity.systemId = change.systemId();
    entity.departmentId = change.departmentId();
    // 保存模板标记及去空白名称，模板本身不能直接分配给员工。
    entity.template = change.template();
    entity.name = change.name().trim();
    // 启停状态参与有效权限计算，停用角色后原分配不会继续生效。
    entity.active = change.active();
    // 请求表达完整选择集：先清空再加入，表示替换，不能误写成累计追加而留下旧权限。
    // contentRevision 确保只改 ElementCollection 时也有实体字段变化，推动 JPA 版本更新。
    entity.permissions.clear();
    entity.permissions.addAll(desired);
    // 显式变更内容修订号，保证仅改权限集合时也会推进角色实体版本。
    entity.contentRevision++;
    // 先刷新角色与集合，再写成功审计；它们仍属于同一事务。
    roles.saveAndFlush(entity);
    audit.record(
        new AuditApi.Event(
            actorId,
            entity.departmentId,
            null,
            entity.systemId,
            "ROLE_CHANGED",
            "role:" + entity.getId(),
            "SUCCESS",
            "角色与显式权限集更新"));
    // 返回新版本与权限编号快照，调用方无法修改持久化集合。
    return view(entity);
  }

  @Override
  @Transactional
  public Role copy(long actorId, long templateId, long departmentId, String name) {
    // 在治理锁内验证操作者对目标部门的管理范围。
    organization.lockManagement(actorId);
    organization.requireDepartmentScope(actorId, departmentId);
    // 先定位模板所属系统，再按系统、角色的统一顺序取锁。
    Role template = role(templateId);
    integration.lock(template.systemId());
    // 锁后重新读取模板事实，避免使用等锁前的旧权限集合。
    template = lock(templateId);
    // 只有有效全局模板可复制，部门角色或停用模板都拒绝。
    if (!template.template() || !template.active()) {
      throw BusinessException.conflict("仅可复制有效全局模板");
    }
    // 复制的是此刻模板的权限编号集合，新角色有独立身份，之后不会随模板自动变化。
    // 同类调用不再经过事务代理，但 copy 已开启事务，save 的逻辑仍参加当前事务。
    return save(
        actorId,
        new RoleChange(
            null, template.systemId(), departmentId, name, false, true, 0, template.permissions()));
  }

  @Override
  @Transactional
  public void delete(long actorId, long roleId, long expectedVersion) {
    // 上层先确认无员工分配；这里取得治理锁后复核角色管理范围。
    organization.lockManagement(actorId);
    var fact = role(roleId);
    // 根据角色真实归属检查范围，不能信任调用方自行声明的部门。
    organization.requireDepartmentScope(actorId, fact.departmentId());
    // 遵循先系统后角色的锁序，与目录上报及角色编辑保持一致。
    integration.lock(fact.systemId());
    var entity = roles.lock(roleId).orElseThrow();
    // 删除确认页面的版本必须仍然有效，否则要求重新确认影响。
    if (entity.getVersion() != expectedVersion) {
      throw BusinessException.conflict("角色已变化，请刷新后确认");
    }
    // 保留历史实体，查询时排除 deleted，同时明确设为停用。
    entity.deleted = true;
    entity.active = false;
    // 撤下权限选择并推进内容版本，阻止旧选择在后续被意外复用。
    entity.permissions.clear();
    entity.contentRevision++;
    // 让逻辑删除和成功审计一起参加当前事务。
    roles.flush();
    audit.record(
        new AuditApi.Event(
            actorId,
            entity.departmentId,
            null,
            entity.systemId,
            "ROLE_DELETED",
            "role:" + roleId,
            "SUCCESS",
            "确认无分配后逻辑删除角色 " + entity.name));
  }

  @Override
  @Transactional
  public boolean report(long systemId, long revision, List<DirectoryItem> items) {
    // 系统身份已由入口校验；锁住该系统以串行处理目录版本。
    var system = integration.lock(systemId);
    // 拒绝停用系统、倒退版本和无效规模；相同版本另走幂等校验。
    if (!system.active()
        || revision < system.directoryRevision()
        || revision < 1
        || items == null
        || items.size() > 10000) {
      throw BusinessException.conflict("系统状态、目录版本或规模不符合要求");
    }
    // 1. 先把完整报文校验完再修改实体；重复 key、缺字段或漏报有效项都整单拒绝。
    Map<String, DirectoryItem> incoming = new HashMap<>();
    // 按稳定权限标识建立索引，同时检查字段长度、空值和重复键。
    for (DirectoryItem item : items) {
      if (item == null
          || !valid(item.key(), 160)
          || !valid(item.module(), 160)
          || !valid(item.feature(), 160)
          || !valid(item.action(), 80)
          || incoming.put(item.key(), item) != null) {
        throw new BusinessException(400, "INVALID_DIRECTORY", "目录资料缺失、过长或权限标识重复");
      }
    }
    // 读取旧目录，完整上报必须显式说明每个仍有效项的状态。
    List<PermissionEntity> existing = permissions.findBySystemId(systemId);
    // 漏报不能隐含撤权，需用 active=false 明确表达停用。
    if (existing.stream().anyMatch(p -> p.active && !incoming.containsKey(p.permissionKey))) {
      throw new BusinessException(400, "INCOMPLETE_DIRECTORY", "有效历史项不能遗漏；停用须明确上报 active=false");
    }
    // 2. 同版本重报只能确认等价内容；返回 false 表示无需更新，不表示上报失败。
    if (revision == system.directoryRevision()) {
      // 同版本仅接受等价重报；已遗漏而停用的历史项不算本次目录内容。
      boolean equal =
          existing.stream()
                  .allMatch(
                      p -> {
                        // 按稳定标识寻找本次项，再比较名称、动作和启停状态。
                        DirectoryItem item = incoming.get(p.permissionKey);
                        // 前面的完整性校验已保证遗漏项只能是停用历史。
                        return item == null || matches(p, item);
                      })
              // 还须反向检查本次标识均已存在，防止同版本偷偷加入新权限。
              && incoming.keySet().stream()
                  .allMatch(k -> existing.stream().anyMatch(p -> p.permissionKey.equals(k)));
      // 任一内容差异都要求新版本；等价重报不写数据也不推进版本。
      if (!equal) {
        throw BusinessException.conflict("相同版本不能携带不同内容");
      }
      return false;
    }
    // 3. 新版本合并稳定标识，保留停用历史；数据库编号不随显示名称修改而变化。
    // seen 跟踪已有稳定键，disabled 收集本次由有效变为停用的数据库编号。
    Set<String> seen = new HashSet<>();
    Set<Long> disabled = new HashSet<>();
    // 先处理历史行，复用其编号以保持角色引用稳定。
    for (PermissionEntity entity : existing) {
      DirectoryItem item = incoming.get(entity.permissionKey);
      // 有效旧项必有本次项；短路判断保证历史停用漏项不会解引用空值。
      if (entity.active && !item.active()) {
        // 这些编号稍后要从所有角色的显式选择中移除。
        disabled.add(entity.getId());
      }
      // 前面完整性检查已排除有效漏项，此处只会保留停用历史。
      if (item == null) {
        entity.active = false;
      } else {
        // 覆盖显示资料和状态，权限身份仍由稳定 key 与原数据库编号确定。
        update(entity, item);
      }
      // 记录已处理的键，下一轮仅为真正新增项创建实体。
      seen.add(entity.permissionKey);
    }
    // 逐项寻找新增权限；新增目录不会自动加入任何角色。
    for (DirectoryItem item : items) {
      if (!seen.contains(item.key())) {
        // 为新权限建立所属系统关系，并复制已校验的目录内容。
        PermissionEntity entity = new PermissionEntity();
        entity.systemId = systemId;
        update(entity, item);
        // 保存新增项，由后续统一 flush 检查数据库约束。
        permissions.save(entity);
      }
    }
    // 先刷新目录；后续角色调整失败时，目录写入仍会随事务回滚。
    permissions.flush();
    // 明确停用时从选择集中撤下，重新启用不会悄悄恢复旧授权；须管理员重新选择。
    if (!disabled.isEmpty()) {
      roles.findAll().stream()
          // 只处理本系统角色，其他系统即使显示名称相同也不受影响。
          .filter(r -> Objects.equals(r.systemId, systemId))
          .forEach(
              r -> {
                // 实际移除了选择才推进角色内容版本，避免无关角色发生版本冲突。
                if (r.permissions.removeAll(disabled)) {
                  r.contentRevision++;
                }
              });
      // 角色选择与目录停用保持同事务，避免只撤目录却保留有效旧选择。
      roles.flush();
    }
    // 4. 目录、受影响角色、内容版本与成功审计同事务提交；任何一步失败都不能推进版本。
    integration.advanceDirectory(systemId, revision);
    // 记录这次系统上报成功；审计失败时目录及版本推进也一并回滚。
    audit.record(
        new AuditApi.Event(
            null,
            null,
            null,
            systemId,
            "DIRECTORY_ACCEPTED",
            "directory:" + revision,
            "SUCCESS",
            "接收完整目录；明确停用项撤下选择，重新启用或新增项不自动授予"));
    // true 表示接受了新版本，区别于同版本等价重报的 false。
    return true;
  }

  private RoleEntity find(long id) {
    return roles
        .findById(id)
        .filter(r -> !r.deleted)
        .orElseThrow(() -> new BusinessException(404, "ROLE_UNKNOWN", "角色不存在"));
  }

  private static Role view(RoleEntity r) {
    return new Role(
        r.getId(),
        r.systemId,
        r.departmentId,
        r.name,
        r.template,
        r.active,
        r.getVersion(),
        Set.copyOf(r.permissions));
  }

  private static Permission view(PermissionEntity p) {
    return new Permission(
        p.getId(),
        p.systemId,
        p.permissionKey,
        p.moduleName,
        p.featureName,
        p.actionName,
        p.active,
        p.getVersion());
  }

  private static boolean valid(String value, int limit) {
    return value != null && !value.isBlank() && value.length() <= limit;
  }

  private static boolean matches(PermissionEntity p, DirectoryItem item) {
    return p.moduleName.equals(item.module())
        && p.featureName.equals(item.feature())
        && p.actionName.equals(item.action())
        && p.active == item.active();
  }

  private static void update(PermissionEntity p, DirectoryItem item) {
    p.permissionKey = item.key();
    p.moduleName = item.module();
    p.featureName = item.feature();
    p.actionName = item.action();
    p.active = item.active();
  }
}
