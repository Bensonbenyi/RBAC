package cn.edu.xmu.rbac.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/** 登录请求，系统顺序决定默认查询的目标系统. */
public record LoginDTO(
    @NotBlank @Size(max = 128) String username,
    @NotBlank @Size(max = 256) String password,
    @NotEmpty List<@NotBlank String> systems) {

  /** 每个目标系统只接受一次. */
  @AssertTrue
  public boolean isUniqueSystems() {
    return systems == null || new HashSet<>(systems).size() == systems.size();
  }
}
