package cn.edu.xmu.rbac.api.department;

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
public class DepartmentController {
  private final DepartmentUseCase service;
  private final CurrentIdentity current;

  public DepartmentController(DepartmentUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/api/departments")
  public Data.Page<Data.Department> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(current.get(), page, size);
  }

  @PostMapping("/api/departments")
  public ResponseEntity<Data.Department> create(
      @RequestBody @Valid Requests.CreateDepartment request) {
    var entity =
        service.create(
            current.get(), CloneFactory.copy(new Data.CreateDepartment(null, null), request));
    return ResponseEntity.created(URI.create("/api/departments/" + entity.id())).body(entity);
  }

  @PatchMapping("/api/departments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(@PathVariable @Positive Long id, @RequestBody @Valid Requests.Name request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/api/departments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }
}
