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

/**
 * 管理系统登记、独立凭据与部门可用范围，为身份和权限模块提供事实.
 *
 * <p>save 和 bind 修改的是管理配置，directoryRevision 由目录上报推进；JPA version
 * 用于发现并发修改。关联范围与系统启用状态只是有效权限判断的一部分，本模块不下发角色副本.
 *
 * <p>SystemFact 内含凭据哈希，只能在可信后端模块间使用；Controller 需要另外构造安全响应.
 */
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
    // 先取得治理锁并限制为最高管理员，系统登记不属于部门内维护。
    organization.lockManagement(actorId);
    global(actorId);
    // 新增构造实体，编辑取得系统行锁，后续版本校验使用锁内状态。
    SystemEntity entity =
        change.id() == null ? new SystemEntity() : systems.lock(change.id()).orElseThrow();
    // 公开客户端标识参与协议识别，编辑时禁止修改并检查页面旧版本。
    if (change.id() != null
        && (entity.getVersion() != change.version() || !entity.systemKey.equals(change.key()))) {
      throw BusinessException.conflict("系统已变化，客户端标识不能修改");
    }
    // 限制标识格式，并保留 -directory 后缀给目录客户端使用。
    if (change.key() == null
        || !change.key().matches("[a-z][a-z0-9-]{1,59}")
        || change.key().endsWith("-directory")
        || change.name() == null
        || change.name().isBlank()) {
      throw new BusinessException(400, "INVALID_SYSTEM", "系统标识或名称格式错误");
    }
    // 分别校验登录和退出回调地址；运行时精确匹配由协议框架执行。
    callback(change.redirectUri());
    callback(change.logoutRedirectUri());
    if (change.id() == null) {
      // 两种凭据独立生成；运行端不得使用目录身份进行员工鉴权。
      var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
      // 两类随机密钥分别编码保存，目录身份不能替代员工登录客户端身份。
      entity.clientSecret = encoder.encode(UUID.randomUUID().toString());
      entity.directorySecret = encoder.encode(UUID.randomUUID().toString());
    }
    // 写入稳定标识和展示名称，名称允许调整，标识保持不变。
    entity.systemKey = change.key();
    entity.name = change.name().trim();
    // 系统停用会参与后续鉴权拒绝，回调配置用于下一次协议交互。
    entity.active = change.active();
    entity.redirectUri = change.redirectUri();
    // 保存退出目标并推进管理配置修订号；目录版本在上报流程单独维护。
    entity.logoutRedirectUri = change.logoutRedirectUri();
    entity.managementRevision++;
    // 先验证持久化约束，再在同一事务写入成功审计。
    systems.saveAndFlush(entity);
    record(actorId, entity.getId(), "SYSTEM_CHANGED", "系统登记或状态更新");
    // 公开事实仍含内部凭据哈希，HTTP 入口必须另行构造脱敏响应。
    return view(entity);
  }

  @Override
  @Transactional
  public Credentials rotateCredentials(long actorId, long systemId, long version) {
    // 先验证最高管理员身份，再锁系统；凭据属于系统全局配置。
    organization.lockManagement(actorId);
    global(actorId);
    // 锁内读取系统，保证与并发编辑、其他轮换按顺序执行。
    SystemEntity entity = systems.lock(systemId).orElseThrow();
    // 要求管理员确认的系统版本仍有效，避免盲目覆盖刚轮换的密钥。
    if (entity.getVersion() != version) {
      throw BusinessException.conflict("系统资料已变化，请刷新后重新确认");
    }
    // 明文只在这次响应中返回，数据库保存编码结果；部署客户端须妥善接收并更新私有配置。
    // 轮换阻止旧密钥进行新的客户端认证，不等同于自动撤销所有已发行员工访问令牌。
    String clientSecret = UUID.randomUUID().toString();
    String directorySecret = UUID.randomUUID().toString();
    // 编码器产生可验证的哈希，明文仅保留在当前方法局部变量中。
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    entity.clientSecret = encoder.encode(clientSecret);
    entity.directorySecret = encoder.encode(directorySecret);
    // 数据库仅保存编码结果；轮换成功审计与变更共同提交。
    systems.flush();
    record(actorId, systemId, "SYSTEM_CREDENTIALS_ROTATED", "重新签发独立凭据，仅响应展示一次");
    // 一次性返回两类明文及新版本，后续普通查询无法取回原文。
    return new Credentials(
        entity.systemKey,
        clientSecret,
        entity.systemKey + "-directory",
        directorySecret,
        entity.getVersion());
  }

  @Override
  @Transactional
  public SystemFact bind(long actorId, long systemId, Set<Long> departments, long version) {
    // 部门可用系统由最高管理员统一设置，先完成治理范围检查。
    organization.lockManagement(actorId);
    global(actorId);
    // 锁住系统并检查预期版本，防止两次全量范围替换互相覆盖。
    SystemEntity entity = systems.lock(systemId).orElseThrow();
    if (entity.getVersion() != version) {
      throw BusinessException.conflict("可用范围已变化");
    }
    // 复制完整目标集合并逐一验证部门有效，全部通过后再改关系。
    Set<Long> desired = Set.copyOf(departments);
    desired.forEach(organization::activeDepartment);
    // 保留已有关系行并切换 active，重新绑定时复用它，避免历史引用及唯一关系被重复创建。
    List<SystemDepartment> existing = links.findBySystemId(systemId);
    // 已有关系与新建关系分开处理，seen 防止重复创建同一对关联。
    Set<Long> seen = new HashSet<>();
    for (SystemDepartment link : existing) {
      // 本次仍选择的关系启用，未选择的关系停用，保留历史行。
      link.active = desired.contains(link.departmentId);
      seen.add(link.departmentId);
    }
    // 只为从未出现过的系统、部门组合新增关联。
    for (Long departmentId : desired) {
      if (!seen.contains(departmentId)) {
        // 建立关联双方编号；新关联默认启用，不复制部门或系统资料。
        SystemDepartment link = new SystemDepartment();
        link.systemId = systemId;
        link.departmentId = departmentId;
        // 把新关系加入当前事务，稍后与系统管理版本一起刷新。
        links.save(link);
      }
    }
    // 范围变更也推进系统实体版本，使旧页面的配置操作检测到冲突。
    entity.managementRevision++;
    systems.flush();
    // 关系更新与系统更新共同参加事务，随后记录完整目标范围。
    links.flush();
    record(actorId, systemId, "SYSTEM_SCOPE", "可用部门更新为 " + desired);
    // 重新组装启用的部门集合，响应包含本次配置后的系统版本。
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

  /**
   * 限制登记回调为绝对 HTTPS 地址，本机 localhost 演示允许 HTTP.
   *
   * <p>这里只校验登记格式；运行时的精确 redirect_uri 匹配由授权服务器框架执行。禁止片段和 URI 用户信息，避免产生难以解释的回调或在地址中夹带凭据.
   */
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
