package cn.edu.xmu.rbac.service.port;

/** System 用例契约，结构化与 DDD 共享. */
public interface SystemUseCase {
  Data.Page<Data.System> list(Data.Identity actor, int page, int size);
}
