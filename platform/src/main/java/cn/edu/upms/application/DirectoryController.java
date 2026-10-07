package cn.edu.upms.application;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.catalog.api.CatalogApi;
import cn.edu.upms.identity.api.IdentityApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 独立目录身份只可上报自身系统；失败不会改变旧目录版本. */
@RestController
public class DirectoryController {
  /** 完整目录快照，版本必须递增或为等价重报. */
  public record Report(long revision, List<CatalogApi.DirectoryItem> items) {}

  private final IdentityApi identity;
  private final CatalogApi catalog;
  private final AuditApi audit;

  /** 注入身份、目录和审计公开能力. */
  public DirectoryController(IdentityApi identity, CatalogApi catalog, AuditApi audit) {
    this.identity = identity;
    this.catalog = catalog;
    this.audit = audit;
  }

  /** 完成身份校验后进入目录事务；事务回滚后独立记录失败. */
  @PostMapping("/api/directory/report")
  public Map<String, Object> report(
      @RequestHeader("Authorization") String header, @RequestBody Report request) {
    long systemId = identity.directory(RuntimeController.bearer(header));
    try {
      boolean changed = catalog.report(systemId, request.revision(), request.items());
      return Map.of("accepted", true, "changed", changed, "revision", request.revision());
    } catch (RuntimeException failure) {
      audit.record(
          new AuditApi.Event(
              null,
              null,
              null,
              systemId,
              "DIRECTORY_FAILED",
              "directory:" + request.revision(),
              "FAILURE",
              "目录未提交，请修正内容或稍后重报"));
      throw failure;
    }
  }
}
