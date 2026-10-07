package cn.edu.upms.integration.internal;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.integration.api.IntegrationApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 校验接入边界与部门可用范围；不执行外部业务，也不向外部下发角色. */
@Service
@Transactional(readOnly = true)
public class IntegrationService implements IntegrationApi {
  private final SystemRepository systems;
  private final LinkRepository links;
  private final OrganizationApi organization;
  private final AuditApi audit;

  /** 注入接入模块存储及组织、审计公开能力. */
  public IntegrationService(
      SystemRepository systems,
      LinkRepository links,
      OrganizationApi organization,
      AuditApi audit) {
    this.systems = systems;
    this.links = links;
    this.organization = organization;
    this.audit = audit;
  }

  @Override
  public SystemFact system(long id) {
    return view(find(id));
  }

  @Override
  public SystemFact byKey(String key) {
    return view(
        systems
            .findBySystemKey(key)
            .orElseThrow(() -> new BusinessException(404, "SYSTEM_UNKNOWN", "系统未登记")));
  }

  @Override
  public List<SystemFact> registered() {
    return systems.findAll().stream().map(this::view).toList();
  }

  @Override
  public List<SystemFact> systems(long actorId) {
    var actor = organization.administrator(actorId);
    return registered().stream()
        .filter(s -> actor.superAdmin() || s.departments().contains(actor.departmentId()))
        .toList();
  }

  @Override
  @Transactional
  public SystemFact save(long actorId, SystemChange change) {
    global(actorId);
    SystemEntity entity =
        change.id() == null ? new SystemEntity() : systems.lock(change.id()).orElseThrow();
    if (change.id() != null
        && (entity.getVersion() != change.version() || !entity.systemKey.equals(change.key()))) {
      throw BusinessException.conflict("系统已变化，客户端标识不能修改");
    }
    if (change.key() == null
        || !change.key().matches("[a-z][a-z0-9-]{1,59}")
        || change.name() == null
        || change.name().isBlank()) {
      throw new BusinessException(400, "INVALID_SYSTEM", "系统标识或名称格式错误");
    }
    callback(change.redirectUri());
    callback(change.logoutRedirectUri());
    if (change.id() == null) {
      // 两种凭据独立生成；运行端不得使用目录身份进行员工鉴权。
      var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
      entity.clientSecret = encoder.encode(UUID.randomUUID().toString());
      entity.directorySecret = encoder.encode(UUID.randomUUID().toString());
    }
    entity.systemKey = change.key();
    entity.name = change.name().trim();
    entity.active = change.active();
    entity.redirectUri = change.redirectUri();
    entity.logoutRedirectUri = change.logoutRedirectUri();
    entity.managementRevision++;
    systems.saveAndFlush(entity);
    record(actorId, entity.getId(), "SYSTEM_CHANGED", "系统登记或状态更新");
    return view(entity);
  }

  @Override
  @Transactional
  public SystemFact bind(long actorId, long systemId, Set<Long> departments, long version) {
    global(actorId);
    SystemEntity entity = systems.lock(systemId).orElseThrow();
    if (entity.getVersion() != version) {
      throw BusinessException.conflict("可用范围已变化");
    }
    Set<Long> desired = Set.copyOf(departments);
    desired.forEach(organization::activeDepartment);
    List<SystemDepartment> existing = links.findBySystemId(systemId);
    Set<Long> seen = new HashSet<>();
    for (SystemDepartment link : existing) {
      link.active = desired.contains(link.departmentId);
      seen.add(link.departmentId);
    }
    for (Long departmentId : desired) {
      if (!seen.contains(departmentId)) {
        SystemDepartment link = new SystemDepartment();
        link.systemId = systemId;
        link.departmentId = departmentId;
        links.save(link);
      }
    }
    entity.managementRevision++;
    systems.flush();
    links.flush();
    record(actorId, systemId, "SYSTEM_SCOPE", "可用部门更新为 " + desired);
    return view(entity);
  }

  @Override
  public boolean available(long systemId, Long departmentId) {
    return departmentId != null
        && find(systemId).active
        && links.existsBySystemIdAndDepartmentIdAndActiveTrue(systemId, departmentId);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SystemFact lock(long systemId) {
    return view(systems.lock(systemId).orElseThrow());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void advanceDirectory(long systemId, long revision) {
    SystemEntity entity = systems.lock(systemId).orElseThrow();
    if (revision <= entity.directoryRevision) {
      throw BusinessException.conflict("目录版本必须递增");
    }
    entity.directoryRevision = revision;
    systems.flush();
  }

  private SystemEntity find(long id) {
    return systems
        .findById(id)
        .orElseThrow(() -> new BusinessException(404, "SYSTEM_UNKNOWN", "系统不存在"));
  }

  private SystemFact view(SystemEntity entity) {
    Set<Long> departments =
        links.findBySystemId(entity.getId()).stream()
            .filter(l -> l.active)
            .map(l -> l.departmentId)
            .collect(Collectors.toUnmodifiableSet());
    return new SystemFact(
        entity.getId(),
        entity.systemKey,
        entity.name,
        entity.active,
        entity.clientSecret,
        entity.directorySecret,
        entity.redirectUri,
        entity.logoutRedirectUri,
        entity.directoryRevision,
        entity.getVersion(),
        departments);
  }

  private void global(long actorId) {
    if (!organization.administrator(actorId).superAdmin()) {
      throw BusinessException.forbidden();
    }
  }

  private void record(long actorId, long systemId, String type, String detail) {
    audit.record(
        new AuditApi.Event(
            actorId, null, null, systemId, type, "system:" + systemId, "SUCCESS", detail));
  }

  private static void callback(String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException invalid) {
      throw new BusinessException(400, "INVALID_CALLBACK", "回调地址格式错误");
    }
    boolean local =
        uri.getHost() != null && Set.of("localhost", "127.0.0.1").contains(uri.getHost());
    if (uri.getFragment() != null
        || uri.getUserInfo() != null
        || uri.getHost() == null
        || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) {
      throw new BusinessException(400, "INVALID_CALLBACK", "回调须为 HTTPS，本机演示可用 HTTP");
    }
  }
}
