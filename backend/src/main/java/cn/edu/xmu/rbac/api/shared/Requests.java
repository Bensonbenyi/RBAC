package cn.edu.xmu.rbac.api.shared;

import cn.edu.xmu.clonefactory.CopyTo;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
  private Requests() {}

  @CopyTo({Data.CreateUser.class})
  public record CreateUser(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 100) String name,
      @NotNull @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {}

  @CopyTo({Data.Profile.class})
  public record Profile(
      @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String name,
      @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {
    @AssertTrue
    public boolean isNonEmpty() {
      return name != null || phone != null;
    }
  }

  @CopyTo({Data.Name.class})
  public record Name(@NotBlank @Size(max = 100) String name) {}

  @CopyTo({Data.CreateDepartment.class})
  public record CreateDepartment(@NotBlank @Size(max = 100) String name, @Positive Long parentId) {}

  @CopyTo({Data.CreateSystem.class})
  public record CreateSystem(
      @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+") String systemKey,
      @NotBlank @Size(max = 100) String name) {}

  @CopyTo({Data.CreatePermission.class})
  public record CreatePermission(
      @NotBlank @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9.:_-]+") String code,
      @NotBlank @Size(max = 100) String name) {}

  public record AssignRole(@NotNull @Positive Long roleId) {}

  public record Login(
      @NotBlank @Size(max = 64) String username,
      @NotNull @Pattern(regexp = "1[3-9][0-9]{9}") String password,
      @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 64) String> systems) {}
}
