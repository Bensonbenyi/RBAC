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

/** 集中处理目录版本、角色归属与显式权限选择；不依赖员工授权存储. */
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
    organization.lockManagement(actorId);
    organization.requireDepartmentScope(actorId, change.departmentId());
    if (change.template() != (change.departmentId() == null)) {
      throw BusinessException.conflict("模板须为全局范围，部门角色须指定部门");
    }
    var system = integration.lock(change.systemId());
    if (!system.active()
        || (!change.template()
            && !integration.available(change.systemId(), change.departmentId()))) {
      throw BusinessException.conflict("系统已停用或未在部门可用范围内");
    }
    if (!change.template()) {
      organization.activeDepartment(change.departmentId());
    }
    RoleEntity entity =
        change.id() == null ? new RoleEntity() : roles.lock(change.id()).orElseThrow();
    if (change.id() != null
        && (entity.deleted
            || entity.getVersion() != change.version()
            || !Objects.equals(entity.departmentId, change.departmentId())
            || !Objects.equals(entity.systemId, change.systemId())
            || entity.template != change.template())) {
      throw BusinessException.conflict("角色已变化，不能改变其归属");
    }
    if (change.name() == null || change.name().isBlank() || change.name().length() > 120) {
      throw new BusinessException(400, "INVALID_ROLE", "角色名称不能为空或超过 120 字符");
    }
    Set<Long> desired = Set.copyOf(change.permissions());
    Map<Long, Permission> facts = new HashMap<>();
    permissions(change.systemId()).forEach(p -> facts.put(p.id(), p));
    for (long id : desired) {
      Permission fact = facts.get(id);
      // 已停用的历史引用可以保留；新增选择只能来自当前有效目录。
      if (fact == null || (!fact.active() && !entity.permissions.contains(id))) {
        throw BusinessException.conflict("权限不属于该系统或已经停用");
      }
    }
    boolean duplicate =
        roles.findAll().stream()
            .anyMatch(
                r ->
                    !Objects.equals(r.getId(), change.id())
                        && Objects.equals(r.systemId, change.systemId())
                        && Objects.equals(r.departmentId, change.departmentId())
                        && r.name.equals(change.name().trim()));
    if (duplicate) {
      throw BusinessException.conflict("相同范围内角色名称重复");
    }
    entity.systemId = change.systemId();
    entity.departmentId = change.departmentId();
    entity.template = change.template();
    entity.name = change.name().trim();
    entity.active = change.active();
    entity.permissions.clear();
    entity.permissions.addAll(desired);
    entity.contentRevision++;
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
    return view(entity);
  }

  @Override
  @Transactional
  public Role copy(long actorId, long templateId, long departmentId, String name) {
    organization.lockManagement(actorId);
    organization.requireDepartmentScope(actorId, departmentId);
    Role template = role(templateId);
    integration.lock(template.systemId());
    template = lock(templateId);
    if (!template.template() || !template.active()) {
      throw BusinessException.conflict("仅可复制有效全局模板");
    }
    return save(
        actorId,
        new RoleChange(
            null, template.systemId(), departmentId, name, false, true, 0, template.permissions()));
  }

  @Override
  @Transactional
  public void delete(long actorId, long roleId, long expectedVersion) {
    organization.lockManagement(actorId);
    var fact = role(roleId);
    organization.requireDepartmentScope(actorId, fact.departmentId());
    integration.lock(fact.systemId());
    var entity = roles.lock(roleId).orElseThrow();
    if (entity.getVersion() != expectedVersion) {
      throw BusinessException.conflict("角色已变化，请刷新后确认");
    }
    entity.deleted = true;
    entity.active = false;
    entity.permissions.clear();
    entity.contentRevision++;
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
    var system = integration.lock(systemId);
    if (!system.active()
        || revision < system.directoryRevision()
        || revision < 1
        || items == null
        || items.size() > 10000) {
      throw BusinessException.conflict("系统状态、目录版本或规模不符合要求");
    }
    Map<String, DirectoryItem> incoming = new HashMap<>();
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
    List<PermissionEntity> existing = permissions.findBySystemId(systemId);
    if (existing.stream().anyMatch(p -> p.active && !incoming.containsKey(p.permissionKey))) {
      throw new BusinessException(400, "INCOMPLETE_DIRECTORY", "有效历史项不能遗漏；停用须明确上报 active=false");
    }
    if (revision == system.directoryRevision()) {
      // 同版本仅接受等价重报；已遗漏而停用的历史项不算本次目录内容。
      boolean equal =
          existing.stream()
                  .allMatch(
                      p -> {
                        DirectoryItem item = incoming.get(p.permissionKey);
                        // 前面的完整性校验已保证遗漏项只能是停用历史。
                        return item == null || matches(p, item);
                      })
              && incoming.keySet().stream()
                  .allMatch(k -> existing.stream().anyMatch(p -> p.permissionKey.equals(k)));
      if (!equal) {
        throw BusinessException.conflict("相同版本不能携带不同内容");
      }
      return false;
    }
    Set<String> seen = new HashSet<>();
    Set<Long> disabled = new HashSet<>();
    for (PermissionEntity entity : existing) {
      DirectoryItem item = incoming.get(entity.permissionKey);
      if (entity.active && !item.active()) {
        disabled.add(entity.getId());
      }
      if (item == null) {
        entity.active = false;
      } else {
        update(entity, item);
      }
      seen.add(entity.permissionKey);
    }
    for (DirectoryItem item : items) {
      if (!seen.contains(item.key())) {
        PermissionEntity entity = new PermissionEntity();
        entity.systemId = systemId;
        update(entity, item);
        permissions.save(entity);
      }
    }
    permissions.flush();
    // 明确停用时从选择集中撤下，重新启用不会悄悄恢复旧授权；须管理员重新选择。
    if (!disabled.isEmpty()) {
      roles.findAll().stream()
          .filter(r -> Objects.equals(r.systemId, systemId))
          .forEach(
              r -> {
                if (r.permissions.removeAll(disabled)) {
                  r.contentRevision++;
                }
              });
      roles.flush();
    }
    integration.advanceDirectory(systemId, revision);
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
