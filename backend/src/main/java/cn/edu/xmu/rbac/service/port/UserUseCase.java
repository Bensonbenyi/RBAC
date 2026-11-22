package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** User 用例契约，结构化与 DDD 共享. */
public interface UserUseCase {
  Data.Page<Data.User> list(Data.Identity actor, int page, int size);

  List<Data.Role> roles(Data.Identity actor, Long id);

  void assign(Data.Identity actor, Long userId, Long roleId);

  void revoke(Data.Identity actor, Long userId, Long roleId);
}
