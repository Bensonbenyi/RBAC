package cn.edu.xmu.rbac.api.role;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.port.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/roles")
public class RoleController {
  private final RoleUseCase service;
  private final CurrentIdentity current;

  public RoleController(RoleUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
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
