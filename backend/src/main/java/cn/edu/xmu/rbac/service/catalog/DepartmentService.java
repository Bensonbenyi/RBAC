package cn.edu.xmu.rbac.service.catalog;

import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.DepartmentUseCase;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Department 目录服务，由两种策略共用. */
@Service
@Transactional
public class DepartmentService implements DepartmentUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  /**
   * 注入部门所需的协作组件.
   *
   * @param store 管理数据读写端口
   * @param access 检查目标许可和部门归属的组件
   */
  public DepartmentService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /**
   * 分页查询部门.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.Department> list(Data.Identity actor, int page, int size) {
    access.require(actor, "department.manage");
    return store.departments(page, size);
  }
}
