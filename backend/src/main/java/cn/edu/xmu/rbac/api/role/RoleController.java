package cn.edu.xmu.rbac.api.role;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.RoleUseCase;
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

/** Role 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
@RequestMapping("/api/roles")
public class RoleController {
  private final RoleUseCase service;
  private final CurrentIdentity current;
  private final SelectionService selections;

  /**
   * 注入当前 HTTP 资源的用例和身份组件.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   * @param selections 提供授权表单候选项的服务
   */
  public RoleController(RoleUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /**
   * 返回当前授权任务可用的候选项，分页上限为 100.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping("/options")
  public ManagementVOs.PageVO<ManagementVOs.RoleVO> options(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(selections.roles(current.get(), page, size), ManagementVOs::role);
  }

  /**
   * 分页查询角色.
   *
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  @GetMapping
  public ManagementVOs.PageVO<ManagementVOs.RoleVO> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(service.list(current.get(), page, size), ManagementVOs::role);
  }

  /**
   * 创建当前部门的角色.
   *
   * @param request 包含新角色名称的请求体
   * @return HTTP 201、角色详情与 Location 响应头
   */
  @PostMapping
  public ResponseEntity<ManagementVOs.RoleVO> create(
      @RequestBody @Valid ManagementDTOs.NameDTO request) {
    Data.Role role = service.create(current.get(), CloneFactory.copy(new Data.Name(null), request));
    return ResponseEntity.created(URI.create("/api/roles/" + role.id()))
        .body(ManagementVOs.role(role));
  }

  /**
   * 修改角色名称.
   *
   * @param id 角色编号
   * @param request 包含新角色名称的请求体
   */
  @PatchMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.NameDTO request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  /**
   * 删除角色.
   *
   * @param id 角色编号
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }

  /**
   * 查询角色的直接权限.
   *
   * @param id 角色编号
   * @return 角色直接获得的权限列表
   */
  @GetMapping("/{id}/permissions")
  public List<ManagementVOs.PermissionVO> permissions(@PathVariable @Positive Long id) {
    return service.permissions(current.get(), id).stream().map(ManagementVOs::permission).toList();
  }

  /**
   * 新建直接授权关系。再次提交由关联主键判定冲突.
   *
   * @param id 角色编号
   * @param request 包含权限编号的请求体
   * @return HTTP 201 和角色授权关联地址
   */
  @PostMapping("/{id}/permissions")
  public ResponseEntity<Void> grant(
      @PathVariable @Positive Long id,
      @RequestBody @Valid ManagementDTOs.GrantPermissionDTO request) {
    service.grant(current.get(), id, request.permissionId());
    return ResponseEntity.created(
            URI.create("/api/roles/" + id + "/permissions/" + request.permissionId()))
        .build();
  }

  /**
   * 撤销角色授权.
   *
   * @param id 角色编号
   * @param permissionId 权限的唯一编号
   */
  @DeleteMapping("/{id}/permissions/{permissionId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable @Positive Long id, @PathVariable @Positive Long permissionId) {
    service.revoke(current.get(), id, permissionId);
  }

  /**
   * 查询角色直接继承的基础角色.
   *
   * @param id 高级角色的编号
   * @return 满足查询条件的角色列表
   */
  @GetMapping("/{id}/inherited-roles")
  public List<ManagementVOs.RoleVO> inheritedRoles(@PathVariable @Positive Long id) {
    return service.inheritedRoles(current.get(), id).stream().map(ManagementVOs::role).toList();
  }

  /**
   * 展示直接与继承权限的去重并集.
   *
   * @param id 角色编号
   * @return 满足查询条件的权限列表
   */
  @GetMapping("/{id}/effective-permissions")
  public List<ManagementVOs.PermissionVO> effectivePermissions(@PathVariable @Positive Long id) {
    return service.effectivePermissions(current.get(), id).stream()
        .map(ManagementVOs::permission)
        .toList();
  }

  /**
   * 添加从高级角色指向基础角色的继承边.
   *
   * @param id 高级角色的编号
   * @param request 包含基础角色编号的请求体
   * @return HTTP 201 和角色继承关联地址
   */
  @PostMapping("/{id}/inherited-roles")
  public ResponseEntity<Void> inherit(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.InheritRoleDTO request) {
    service.inherit(current.get(), id, request.juniorId());
    return ResponseEntity.created(
            URI.create("/api/roles/" + id + "/inherited-roles/" + request.juniorId()))
        .build();
  }

  /**
   * 解除指定角色继承关系.
   *
   * @param id 高级角色的编号
   * @param juniorId 基础角色的唯一编号
   */
  @DeleteMapping("/{id}/inherited-roles/{juniorId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void uninherit(@PathVariable @Positive Long id, @PathVariable @Positive Long juniorId) {
    service.uninherit(current.get(), id, juniorId);
  }
}
