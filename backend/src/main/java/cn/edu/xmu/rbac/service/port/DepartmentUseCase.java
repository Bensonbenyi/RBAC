package cn.edu.xmu.rbac.service.port;

/** Department 用例契约，结构化与 DDD 共享. */
public interface DepartmentUseCase {
  /**
   * 分页查询部门.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  Data.Page<Data.Department> list(Data.Identity actor, int page, int size);
}
