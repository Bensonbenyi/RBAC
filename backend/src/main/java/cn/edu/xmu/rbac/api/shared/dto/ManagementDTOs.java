package cn.edu.xmu.rbac.api.shared.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 输入字段白名单和 Controller 参数约束. */
public final class ManagementDTOs {
  private ManagementDTOs() {}

  /** Name 数据契约. */
  @CopyTo({Data.Name.class})
  public record NameDTO(@NotBlank @Size(max = 100) String name) {}

  /** AssignRole 数据契约. */
  public record AssignRoleDTO(@NotNull @Positive Long roleId) {}

  /** 新建角色与权限的直接关联. */
  public record GrantPermissionDTO(@NotNull @Positive Long permissionId) {}

  /** 指定要继承的基础角色. */
  public record InheritRoleDTO(@NotNull @Positive Long juniorId) {}
}
