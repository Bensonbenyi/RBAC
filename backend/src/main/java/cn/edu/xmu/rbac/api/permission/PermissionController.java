package cn.edu.xmu.rbac.api.permission;

import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.port.PermissionUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
public class PermissionController {
  private final PermissionUseCase service;
  private final CurrentIdentity current;
  private final SelectionService selections;

  /**
   * 注入当前 HTTP 资源的用例和身份组件.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   * @param selections 提供授权表单候选项的服务
   */
  public PermissionController(
      PermissionUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /**
   * 返回当前授权任务可用的候选项，分页上限为 100.
   *
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping("/api/permissions/options")
  public ManagementVOs.PageVO<ManagementVOs.PermissionVO> options(
      @RequestParam @Positive Long systemId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(
        selections.permissions(current.get(), systemId, page, size), ManagementVOs::permission);
  }

  /**
   * 分页查询权限.
   *
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping("/api/systems/{systemId}/permissions")
  public ManagementVOs.PageVO<ManagementVOs.PermissionVO> list(
      @PathVariable @Positive Long systemId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(
        service.list(current.get(), systemId, page, size), ManagementVOs::permission);
  }
}
