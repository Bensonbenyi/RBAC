package cn.edu.xmu.rbac.api.user;

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
@RequestMapping("/api/users")
public class UserController {
  private final UserUseCase service;
  private final CurrentIdentity current;

  public UserController(UserUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping
  public Data.Page<Data.User> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), page, size);
  }

  @GetMapping("/{id}")
  public Data.User get(@PathVariable @Positive Long id) {
    return service.get(current.get(), id);
  }

  @PostMapping
  public ResponseEntity<Data.User> create(@RequestBody @Valid Requests.CreateUser request) {
    var user =
        service.create(
            current.get(), CloneFactory.copy(new Data.CreateUser(null, null, null), request));
    return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
  }

  @PatchMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(
      @PathVariable @Positive Long id, @RequestBody @Valid Requests.Profile request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Profile(null, null), request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }

  @GetMapping("/{id}/roles")
  public List<Data.Role> roles(@PathVariable @Positive Long id) {
    return service.roles(current.get(), id);
  }

  @PostMapping("/{id}/roles")
  public ResponseEntity<Void> assign(
      @PathVariable @Positive Long id, @RequestBody @Valid Requests.AssignRole request) {
    service.assign(current.get(), id, request.roleId());
    return ResponseEntity.created(URI.create("/api/users/" + id + "/roles/" + request.roleId()))
        .build();
  }

  @DeleteMapping("/{id}/roles/{roleId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable @Positive Long id, @PathVariable @Positive Long roleId) {
    service.revoke(current.get(), id, roleId);
  }
}
