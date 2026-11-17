package cn.edu.xmu.rbac.api.role;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.RoleUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Role 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
@RequestMapping("/api/roles")
public class RoleController {
  private final RoleUseCase service;
  private final CurrentIdentity current;
  private final SelectionService selections;

  /** 装配RoleController所需依赖. */
  public RoleController(RoleUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /** 返回当前授权任务可用的候选项，分页上限为 100. */
  @GetMapping("/options")
  public Data.Page<Data.Role> options(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return selections.roles(current.get(), page, size);
  }

  @GetMapping
  public Data.Page<Data.Role> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), page, size);
  }

  @GetMapping("/{id}")
  public Data.Role get(@PathVariable @Positive Long id) {
    return service.get(current.get(), id);
  }

  @PostMapping
  public ResponseEntity<Data.Role> create(@RequestBody @Valid Requests.Name request) {
    var role = service.create(current.get(), CloneFactory.copy(new Data.Name(null), request));
    return ResponseEntity.created(URI.create("/api/roles/" + role.id())).body(role);
  }

  @PatchMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(@PathVariable @Positive Long id, @RequestBody @Valid Requests.Name request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }

  @GetMapping("/{id}/permissions")
  public List<Data.Permission> permissions(@PathVariable @Positive Long id) {
    return service.permissions(current.get(), id);
  }

  @PutMapping("/{id}/permissions/{permissionId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void grant(@PathVariable @Positive Long id, @PathVariable @Positive Long permissionId) {
    service.grant(current.get(), id, permissionId);
  }

  @DeleteMapping("/{id}/permissions/{permissionId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable @Positive Long id, @PathVariable @Positive Long permissionId) {
    service.revoke(current.get(), id, permissionId);
  }
}
