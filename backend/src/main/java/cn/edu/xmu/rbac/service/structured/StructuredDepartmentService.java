package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.DepartmentUseCase;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Department 用例的结构化事务脚本实现. */
@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredDepartmentService implements DepartmentUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  public StructuredDepartmentService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  public Data.Page<Data.Department> list(Data.Identity actor, int page, int size) {
    access.require(actor, "department.manage");
    return store.departments(page, size);
  }

  /** 校验输入后创建对象. */
  public Data.Department create(Data.Identity actor, Data.CreateDepartment command) {
    access.require(actor, "department.manage");
    if (command.parentId() != null) {
      store.department(command.parentId());
    }
    return store.createDepartment(command);
  }

  /** 修改白名单字段并保持归属不变. */
  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "department.manage");
    store.department(id);
    store.renameDepartment(id, command.name());
  }

  /** 检查引用关系后删除对象. */
  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "department.manage");
    store.department(id);
    store.deleteDepartment(id);
  }
}
