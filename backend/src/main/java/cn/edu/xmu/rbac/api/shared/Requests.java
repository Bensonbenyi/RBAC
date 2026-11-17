package cn.edu.xmu.rbac.api.shared;

import cn.edu.xmu.clonefactory.CopyTo;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 输入字段白名单和 Controller 参数约束. */
public final class Requests {
  private Requests() {}

  /** CreateUser 数据契约. */
  @CopyTo({Data.CreateUser.class})
  public record CreateUser(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 100) String name,
      @NotNull @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {}

  /** Profile 数据契约. */
  @CopyTo({Data.Profile.class})
  public record Profile(
      @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
      @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {
    @AssertTrue
    public boolean isNonEmpty() {
      return name != null || phone != null;
    }
  }

  /** Name 数据契约. */
  @CopyTo({Data.Name.class})
  public record Name(@NotBlank @Size(max = 100) String name) {}

  /** CreateDepartment 数据契约. */
  @CopyTo({Data.CreateDepartment.class})
  public record CreateDepartment(@NotBlank @Size(max = 100) String name, @Positive Long parentId) {}

  /** CreateSystem 数据契约. */
  @CopyTo({Data.CreateSystem.class})
  public record CreateSystem(
      @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+") String systemKey,
      @NotBlank @Size(max = 100) String name) {}

  /** CreatePermission 数据契约. */
  @CopyTo({Data.CreatePermission.class})
  public record CreatePermission(
      @NotBlank @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9.:_-]+") String code,
      @NotBlank @Size(max = 100) String name) {}

  /** AssignRole 数据契约. */
  public record AssignRole(@NotNull @Positive Long roleId) {}

  /** Login 数据契约. */
  public record Login(
      @NotBlank @Size(max = 64) String username,
      @NotNull @Pattern(regexp = "1[3-9][0-9]{9}") String password,
      @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 64) String> systems) {}
}
