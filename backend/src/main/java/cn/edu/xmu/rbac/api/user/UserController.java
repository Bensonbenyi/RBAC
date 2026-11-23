package cn.edu.xmu.rbac.api.user;

import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
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

  /**
   * 注入用户所需的协作组件.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   */
  public UserController(UserUseCase service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /**
   * 分页查询用户.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping
  public ManagementVOs.PageVO<ManagementVOs.UserVO> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(service.list(current.get(), page, size), ManagementVOs::user);
  }

  /**
   * 查询已分配角色.
   *
   * @param id 用户编号
   * @return 满足查询条件的角色列表
   */
  @GetMapping("/{id}/roles")
  public List<ManagementVOs.RoleVO> roles(@PathVariable @Positive Long id) {
    return service.roles(current.get(), id).stream().map(ManagementVOs::role).toList();
  }

  /**
   * 在本部门范围内分配角色.
   *
   * @param id 用户编号
   * @param request 包含目标角色编号的请求体
   * @return HTTP 201 和用户角色关联地址
   */
  @PostMapping("/{id}/roles")
  public ResponseEntity<Void> assign(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.AssignRoleDTO request) {
    service.assign(current.get(), id, request.roleId());
    return ResponseEntity.created(URI.create("/api/users/" + id + "/roles/" + request.roleId()))
        .build();
  }

  /**
   * 撤销用户角色关联.
   *
   * @param id 用户编号
   * @param roleId 角色的唯一编号
   */
  @DeleteMapping("/{id}/roles/{roleId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable @Positive Long id, @PathVariable @Positive Long roleId) {
    service.revoke(current.get(), id, roleId);
  }
}
