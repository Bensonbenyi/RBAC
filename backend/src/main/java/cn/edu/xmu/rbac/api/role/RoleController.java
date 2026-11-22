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

  /** 装配RoleController所需依赖. */
  public RoleController(RoleUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /** 返回当前授权任务可用的候选项，分页上限为 100. */
  @GetMapping("/options")
  public ManagementVOs.PageVO<ManagementVOs.RoleVO> options(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(selections.roles(current.get(), page, size), ManagementVOs::role);
  }

  @GetMapping
  public ManagementVOs.PageVO<ManagementVOs.RoleVO> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(service.list(current.get(), page, size), ManagementVOs::role);
  }

  /** 创建当前部门的角色. */
  @PostMapping
  public ResponseEntity<ManagementVOs.RoleVO> create(
      @RequestBody @Valid ManagementDTOs.NameDTO request) {
    Data.Role role = service.create(current.get(), CloneFactory.copy(new Data.Name(null), request));
    return ResponseEntity.created(URI.create("/api/roles/" + role.id()))
        .body(ManagementVOs.role(role));
  }

  @PatchMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.NameDTO request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }

  @GetMapping("/{id}/permissions")
  public List<ManagementVOs.PermissionVO> permissions(@PathVariable @Positive Long id) {
    return service.permissions(current.get(), id).stream().map(ManagementVOs::permission).toList();
  }

  /** 新建直接授权关系。再次提交由关联主键判定冲突. */
  @PostMapping("/{id}/permissions")
  public ResponseEntity<Void> grant(
      @PathVariable @Positive Long id,
      @RequestBody @Valid ManagementDTOs.GrantPermissionDTO request) {
    service.grant(current.get(), id, request.permissionId());
    return ResponseEntity.created(
            URI.create("/api/roles/" + id + "/permissions/" + request.permissionId()))
        .build();
  }

  @DeleteMapping("/{id}/permissions/{permissionId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable @Positive Long id, @PathVariable @Positive Long permissionId) {
    service.revoke(current.get(), id, permissionId);
  }

  /** 查询角色直接继承的基础角色. */
  @GetMapping("/{id}/inherited-roles")
  public List<ManagementVOs.RoleVO> inheritedRoles(@PathVariable @Positive Long id) {
    return service.inheritedRoles(current.get(), id).stream().map(ManagementVOs::role).toList();
  }

  /** 展示直接与继承权限的去重并集. */
  @GetMapping("/{id}/effective-permissions")
  public List<ManagementVOs.PermissionVO> effectivePermissions(@PathVariable @Positive Long id) {
    return service.effectivePermissions(current.get(), id).stream()
        .map(ManagementVOs::permission)
        .toList();
  }

  /** 添加从高级角色指向基础角色的继承边. */
  @PostMapping("/{id}/inherited-roles")
  public ResponseEntity<Void> inherit(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.InheritRoleDTO request) {
    service.inherit(current.get(), id, request.juniorId());
    return ResponseEntity.created(
            URI.create("/api/roles/" + id + "/inherited-roles/" + request.juniorId()))
        .build();
  }

  @DeleteMapping("/{id}/inherited-roles/{juniorId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void uninherit(@PathVariable @Positive Long id, @PathVariable @Positive Long juniorId) {
    service.uninherit(current.get(), id, juniorId);
  }
}
