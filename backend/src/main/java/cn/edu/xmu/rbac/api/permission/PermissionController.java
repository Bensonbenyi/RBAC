package cn.edu.xmu.rbac.api.permission;

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
public class PermissionController {
  private final PermissionUseCase service;
  private final CurrentIdentity current;

  public PermissionController(PermissionUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/api/systems/{systemId}/permissions")
  public Data.Page<Data.Permission> list(
      @PathVariable @Positive Long systemId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), systemId, page, size);
  }

  @PostMapping("/api/systems/{systemId}/permissions")
  public ResponseEntity<Data.Permission> create(
      @PathVariable @Positive Long systemId,
      @RequestBody @Valid Requests.CreatePermission request) {
    var entity =
        service.create(
            current.get(),
            systemId,
            CloneFactory.copy(new Data.CreatePermission(null, null), request));
    return ResponseEntity.created(URI.create("/api/permissions/" + entity.id())).body(entity);
  }

  @PatchMapping("/api/permissions/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(@PathVariable @Positive Long id, @RequestBody @Valid Requests.Name request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/api/permissions/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }
}
