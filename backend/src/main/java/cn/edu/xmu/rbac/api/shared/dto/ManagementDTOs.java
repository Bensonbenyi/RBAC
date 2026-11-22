package cn.edu.xmu.rbac.api.shared.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 输入字段白名单和 Controller 参数约束. */
public final class ManagementDTOs {
  private ManagementDTOs() {}

  /** Name 数据契约. */
  @CopyTo({Data.Name.class})
  public record NameDTO(@NotBlank @Size(max = 100) String name) {}

  /** CreateSystem 数据契约. */
  @CopyTo({Data.CreateSystem.class})
  public record CreateSystemDTO(
      @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+") String systemKey,
      @NotBlank @Size(max = 100) String name) {}

  /** CreatePermission 数据契约. */
  @CopyTo({Data.CreatePermission.class})
  public record CreatePermissionDTO(
      @NotBlank @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9.:_-]+") String code,
      @NotBlank @Size(max = 100) String name) {}

  /** AssignRole 数据契约. */
  public record AssignRoleDTO(@NotNull @Positive Long roleId) {}

  /** 新建角色与权限的直接关联. */
  public record GrantPermissionDTO(@NotNull @Positive Long permissionId) {}

  /** 指定要继承的基础角色. */
  public record InheritRoleDTO(@NotNull @Positive Long juniorId) {}
}
