package cn.edu.xmu.rbac.service.port;

import java.util.List;

public interface RoleUseCase {
  Data.Page<Data.Role> list(Data.Identity actor, int page, int size);

  Data.Role get(Data.Identity actor, Long id);

  Data.Role create(Data.Identity actor, Data.Name command);

  void update(Data.Identity actor, Long id, Data.Name command);

  void delete(Data.Identity actor, Long id);

  List<Data.Permission> permissions(Data.Identity actor, Long id);

  void grant(Data.Identity actor, Long id, Long permissionId);

  void revoke(Data.Identity actor, Long id, Long permissionId);
}
