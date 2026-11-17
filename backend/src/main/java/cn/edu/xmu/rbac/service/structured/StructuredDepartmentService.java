package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredDepartmentService implements DepartmentUseCase {
  private static final Logger log = LoggerFactory.getLogger(StructuredDepartmentService.class);
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

  public Data.Department create(Data.Identity actor, Data.CreateDepartment command) {
    access.require(actor, "department.manage");
    if (command.parentId() != null) store.department(command.parentId());
    log.debug("createDepartment actor={}", actor.userId());
    return store.createDepartment(command);
  }

  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "department.manage");
    store.department(id);
    store.renameDepartment(id, command.name());
    log.debug("renameDepartment actor={} target={}", actor.userId(), id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "department.manage");
    store.department(id);
    store.deleteDepartment(id);
    log.debug("deleteDepartment actor={} target={}", actor.userId(), id);
  }
}
