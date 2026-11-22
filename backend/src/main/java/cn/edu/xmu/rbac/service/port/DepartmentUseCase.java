package cn.edu.xmu.rbac.service.port;

/** Department 用例契约，结构化与 DDD 共享. */
public interface DepartmentUseCase {
  Data.Page<Data.Department> list(Data.Identity actor, int page, int size);
}
