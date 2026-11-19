package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

/** 部门角色继承图，保证新增有向边保持无环。高级角色获得基础角色权限. */
public final class RoleHierarchy {
  private final List<Data.Inheritance> edges;

  public RoleHierarchy(List<Data.Inheritance> edges) {
    this.edges = List.copyOf(edges);
  }

  /** 从基础角色沿继承方向搜索，能够回到高级角色时拒绝新增边. */
  public void requireAcyclic(Long seniorId, Long juniorId) {
    ArrayDeque<Long> pending = new ArrayDeque<>();
    HashSet<Long> visited = new HashSet<>();
    pending.add(juniorId);
    while (!pending.isEmpty()) {
      Long current = pending.removeFirst();
      if (current.equals(seniorId)) {
        throw new BusinessException(409, "ROLE_CYCLE", "角色继承不能形成环");
      }
      if (visited.add(current)) {
        edges.stream()
            .filter(edge -> edge.seniorRoleId().equals(current))
            .map(Data.Inheritance::juniorRoleId)
            .forEach(pending::addLast);
      }
    }
  }
}
