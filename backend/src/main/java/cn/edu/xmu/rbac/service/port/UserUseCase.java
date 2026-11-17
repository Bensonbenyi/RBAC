package cn.edu.xmu.rbac.service.port;

import java.util.List;

public interface UserUseCase {
  Data.Page<Data.User> list(Data.Identity actor, int page, int size);

  Data.User get(Data.Identity actor, Long id);

  Data.User create(Data.Identity actor, Data.CreateUser command);

  void update(Data.Identity actor, Long id, Data.Profile command);

  void delete(Data.Identity actor, Long id);

  void updateMe(Data.Identity actor, Data.Profile command);

  List<Data.Role> roles(Data.Identity actor, Long id);

  void assign(Data.Identity actor, Long userId, Long roleId);

  void revoke(Data.Identity actor, Long userId, Long roleId);
}
