package cn.edu.xmu.rbac.service.role;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

/** 角色继承的无环规则，继承边从高级角色指向基础角色. */
public final class RoleHierarchy {
  private RoleHierarchy() {}

  /** 加入高级到基础的边之前，检查基础角色能否走回高级角色. */
  public static void requireAcyclic(List<Data.Inheritance> edges, Long seniorId, Long juniorId) {
    ArrayDeque<Long> pending = new ArrayDeque<>();
    HashSet<Long> seen = new HashSet<>();
    pending.add(juniorId);
    seen.add(juniorId);
    while (!pending.isEmpty()) {
      Long current = pending.removeFirst();
      if (current.equals(seniorId)) {
        throw new BusinessException(409, "ROLE_CYCLE", "角色继承不能形成环");
      }
      for (Data.Inheritance edge : edges) {
        if (edge.seniorRoleId().equals(current) && seen.add(edge.juniorRoleId())) {
          pending.addLast(edge.juniorRoleId());
        }
      }
    }
  }
}
