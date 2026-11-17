package cn.edu.xmu.rbac.api.system;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.SystemUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** System 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
public class SystemController {
  private final SystemUseCase service;
  private final CurrentIdentity current;
  private final SelectionService selections;

  /** 装配SystemController所需依赖. */
  public SystemController(
      SystemUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /** 返回当前授权任务可用的候选项，分页上限为 100. */
  @GetMapping("/api/systems/options")
  public Data.Page<Data.System> options(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return selections.systems(current.get(), page, size);
  }

  @GetMapping("/api/systems")
  public Data.Page<Data.System> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), page, size);
  }

  /** 校验输入后创建对象. */
  @PostMapping("/api/systems")
  public ResponseEntity<Data.System> create(@RequestBody @Valid Requests.CreateSystem request) {
    var entity =
        service.create(
            current.get(), CloneFactory.copy(new Data.CreateSystem(null, null), request));
    return ResponseEntity.created(URI.create("/api/systems/" + entity.id())).body(entity);
  }

  @PatchMapping("/api/systems/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(@PathVariable @Positive Long id, @RequestBody @Valid Requests.Name request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/api/systems/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }
}
