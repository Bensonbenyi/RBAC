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

  /**
   * 注入身份目录服务和当前操作者.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   */
  public IdentityUserController(IdentityDirectoryService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /**
   * 查询与统一身份标识关联的工号和姓名.
   *
   * @param userId 用户的唯一编号
   * @return 用于课程系统绑定的身份资料
   */
  @GetMapping("/auth/users/{userId}")
  public UserVO get(@PathVariable @NotBlank String userId) {
    Data.User user = service.lookup(current.get(), userId);
    return new UserVO(user.id().toString(), user.username(), user.name());
  }
}
