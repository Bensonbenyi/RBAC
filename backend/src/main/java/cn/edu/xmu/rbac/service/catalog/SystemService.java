package cn.edu.xmu.rbac.service.catalog;

import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SystemUseCase;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** System 目录服务，由两种策略共用. */
@Service
@Transactional
public class SystemService implements SystemUseCase {
  private final ManagementStore store;
  private final AccessPolicy access;

  /**
   * 注入业务系统所需的协作组件.
   *
   * @param store 管理数据读写端口
   * @param access 检查目标许可和部门归属的组件
   */
  public SystemService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /**
   * 分页查询业务系统.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param page 从 0 开始的页号
   * @param size 每页记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.System> list(Data.Identity actor, int page, int size) {
    access.require(actor, "system.manage");
    return store.systems(page, size);
  }
}
