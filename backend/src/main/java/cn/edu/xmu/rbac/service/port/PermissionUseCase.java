package cn.edu.xmu.rbac.service.port;

/** Permission 用例契约，结构化与 DDD 共享. */
public interface PermissionUseCase {
  /**
   * 分页查询权限.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param systemId 权限所属的业务系统编号
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Permission> list(Data.Identity actor, Long systemId, int page, int size);
}
