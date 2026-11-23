package cn.edu.xmu.rbac.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/**
 * 登录请求，系统顺序决定默认查询的目标系统.
 *
 * @param username 唯一登录账号
 * @param password 登录手机号
 * @param systems 拟访问的系统标识列表，首项是默认目标
 */
public record LoginDTO(
    @NotBlank @Size(max = 128) String username,
    @NotBlank @Size(max = 256) String password,
    @NotEmpty List<@NotBlank String> systems) {

  /**
   * 检查每个目标系统是否只出现一次.
   *
   * @return 系统列表为空引用或元素互不重复时为 true
   */
  @AssertTrue
  public boolean isUniqueSystems() {
    return systems == null || new HashSet<>(systems).size() == systems.size();
  }
}
