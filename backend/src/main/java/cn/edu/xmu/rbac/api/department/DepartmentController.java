package cn.edu.xmu.rbac.api.department;

import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.port.DepartmentUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
