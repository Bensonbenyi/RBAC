package cn.edu.xmu.rbac.service.role;

import cn.edu.xmu.rbac.service.port.BusinessException;
import java.util.Set;

/** 继承边由高级角色指向基础角色. */
public final class RoleHierarchy {
  /** 隐藏工具类构造入口. */
  private RoleHierarchy() {}

  /**
   * 判断新增继承边是否会形成环.
   *
   * @param reachableFromJunior 从拟继承的基础角色出发可达的编号集合
   * @param seniorId 高级角色的唯一编号
   * @throws BusinessException 添加该关联会形成继承环
   */
  public static void requireAcyclic(Set<Long> reachableFromJunior, Long seniorId) {
    if (reachableFromJunior.contains(seniorId)) {
      throw new BusinessException(409, "ROLE_CYCLE", "角色继承不能形成环");
    }
  }
}
