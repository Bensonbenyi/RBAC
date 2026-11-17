package cn.edu.xmu.rbac.api.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.UserUseCase;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User 资源的 HTTP 输入与响应映射. */
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

  /** 校验输入后创建对象. */
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

  /** 在本部门范围内分配角色. */
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
