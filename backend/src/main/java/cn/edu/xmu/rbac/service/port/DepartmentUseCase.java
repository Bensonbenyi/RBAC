package cn.edu.xmu.rbac.service.port;

public interface DepartmentUseCase {
  Data.Page<Data.Department> list(Data.Identity actor, int page, int size);

  Data.Department create(Data.Identity actor, Data.CreateDepartment command);

  void update(Data.Identity actor, Long id, Data.Name command);

  void delete(Data.Identity actor, Long id);
}
