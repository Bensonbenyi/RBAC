package cn.edu.xmu.rbac.api.permission;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.api.shared.vo.ManagementVOs;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.PermissionUseCase;
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

/** Permission 资源的 HTTP 输入与响应映射. */
@RestController
@Validated
public class PermissionController {
  private final PermissionUseCase service;
  private final CurrentIdentity current;
  private final SelectionService selections;

  /** 装配PermissionController所需依赖. */
  public PermissionController(
      PermissionUseCase service, CurrentIdentity current, SelectionService selections) {
    this.service = service;
    this.current = current;
    this.selections = selections;
  }

  /** 返回当前授权任务可用的候选项，分页上限为 100. */
  @GetMapping("/api/permissions/options")
  public ManagementVOs.PageVO<ManagementVOs.PermissionVO> options(
      @RequestParam @Positive Long systemId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(
        selections.permissions(current.get(), systemId, page, size), ManagementVOs::permission);
  }

  @GetMapping("/api/systems/{systemId}/permissions")
  public ManagementVOs.PageVO<ManagementVOs.PermissionVO> list(
      @PathVariable @Positive Long systemId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ManagementVOs.page(
        service.list(current.get(), systemId, page, size), ManagementVOs::permission);
  }

  /** 校验输入后创建对象. */
  @PostMapping("/api/systems/{systemId}/permissions")
  public ResponseEntity<ManagementVOs.PermissionVO> create(
      @PathVariable @Positive Long systemId,
      @RequestBody @Valid ManagementDTOs.CreatePermissionDTO request) {
    Data.Permission entity =
        service.create(
            current.get(),
            systemId,
            CloneFactory.copy(new Data.CreatePermission(null, null), request));
    return ResponseEntity.created(URI.create("/api/permissions/" + entity.id()))
        .body(ManagementVOs.permission(entity));
  }

  @PatchMapping("/api/permissions/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void update(
      @PathVariable @Positive Long id, @RequestBody @Valid ManagementDTOs.NameDTO request) {
    service.update(current.get(), id, CloneFactory.copy(new Data.Name(null), request));
  }

  @DeleteMapping("/api/permissions/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable @Positive Long id) {
    service.delete(current.get(), id);
  }
}
