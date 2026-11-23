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

  /**
   * 注入部门所需的协作组件.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   */
  public DepartmentController(DepartmentUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /**
   * 分页查询部门.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping("/api/departments")
  public ManagementVOs.PageVO<ManagementVOs.DepartmentVO> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(service.list(current.get(), page, size), ManagementVOs::department);
  }
}
