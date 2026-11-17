package cn.edu.xmu.rbac.service.port;

public interface PermissionUseCase {
  Data.Page<Data.Permission> list(Data.Identity actor, Long systemId, int page, int size);

  Data.Permission create(Data.Identity actor, Long systemId, Data.CreatePermission command);

  void update(Data.Identity actor, Long id, Data.Name command);

  void delete(Data.Identity actor, Long id);
}
