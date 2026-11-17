package cn.edu.xmu.rbac.api.system;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.port.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class SystemController {
  private final SystemUseCase service;
  private final CurrentIdentity current;

  public SystemController(SystemUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/api/systems")
  public Data.Page<Data.System> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), page, size);
  }

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
