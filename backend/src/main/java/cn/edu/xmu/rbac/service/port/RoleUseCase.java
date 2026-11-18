package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** Role 用例契约，结构化与 DDD 共享. */
public interface RoleUseCase {
  Data.Page<Data.Role> list(Data.Identity actor, int page, int size);

  Data.Role get(Data.Identity actor, Long id);

  Data.Role create(Data.Identity actor, Data.Name command);

  void update(Data.Identity actor, Long id, Data.Name command);

  void delete(Data.Identity actor, Long id);

  List<Data.Permission> permissions(Data.Identity actor, Long id);

  void grant(Data.Identity actor, Long id, Long permissionId);

  void revoke(Data.Identity actor, Long id, Long permissionId);

  /** 查询直接继承的基础角色. */
  List<Data.Role> inheritedRoles(Data.Identity actor, Long id);

  /** 查询直接与继承权限的去重并集. */
  List<Data.Permission> effectivePermissions(Data.Identity actor, Long id);

  void inherit(Data.Identity actor, Long id, Long juniorId);

  void uninherit(Data.Identity actor, Long id, Long juniorId);
}
