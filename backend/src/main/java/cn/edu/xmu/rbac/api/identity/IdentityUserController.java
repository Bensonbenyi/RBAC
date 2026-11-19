package cn.edu.xmu.rbac.api.identity;

import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.UserVO;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.IdentityDirectoryService;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 按统一身份标识返回教师选取所需的公开资料. */
@RestController
@Validated
public class IdentityUserController {
  private final IdentityDirectoryService service;
  private final CurrentIdentity current;

  /** 注入身份目录服务和当前操作者. */
  public IdentityUserController(IdentityDirectoryService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /** 查询身份标识对应的姓名及工号. */
  @GetMapping("/auth/users/{userId}")
  public UserVO get(@PathVariable @NotBlank String userId) {
    Data.User user = service.lookup(current.get(), userId);
    return new UserVO(user.id().toString(), user.username(), user.name());
  }
}
