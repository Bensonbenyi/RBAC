package cn.edu.xmu.rbac.api.department;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.DepartmentUseCase;
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

/** Department 资源的 HTTP 输入与响应映射. */
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
  public ManagementVOs.PageVO<ManagementVOs.DepartmentVO> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(service.list(current.get(), page, size), ManagementVOs::department);
  }

  /** 校验输入后创建对象. */
  @PostMapping("/api/departments")
  public ResponseEntity<ManagementVOs.DepartmentVO> create(
      @RequestBody @Valid ManagementDTOs.CreateDepartmentDTO request) {
    Data.Department entity =
        service.create(
            current.get(), CloneFactory.copy(new Data.CreateDepartment(null, null), request));
    return ResponseEntity.created(URI.create("/api/departments/" + entity.id()))
        .body(ManagementVOs.department(entity));
  }

  @PatchMapping("/api/departments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.NameDTO request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/api/departments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }
}
