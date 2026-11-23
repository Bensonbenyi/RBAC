package cn.edu.xmu.rbac.api.shared.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 输入字段白名单和 Controller 参数约束. */
public final class ManagementDTOs {
  /** 隐藏工具类构造入口. */
  private ManagementDTOs() {}

  /**
   * 新建或改名时提交的角色名称.
   *
   * @param name 角色名称
   */
  @CopyTo({Data.Name.class})
  public record NameDTO(@NotBlank @Size(max = 100) String name) {}

  /**
   * 为用户分配角色的请求.
   *
   * @param roleId 角色的唯一编号
   */
  public record AssignRoleDTO(@NotNull @Positive Long roleId) {}

  /**
   * 新建角色与权限的直接关联.
   *
   * @param permissionId 权限的唯一编号
   */
  public record GrantPermissionDTO(@NotNull @Positive Long permissionId) {}

  /**
   * 指定要继承的基础角色.
   *
   * @param juniorId 基础角色的唯一编号
   */
  public record InheritRoleDTO(@NotNull @Positive Long juniorId) {}
}
