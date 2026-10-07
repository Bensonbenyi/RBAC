package cn.edu.upms.authorization.internal;

import java.util.Set;

/** 不依赖 Spring、数据库或缓存的有效权限规则，读写用例采用相同语义. */
final class EffectivePermissionRule {
  /** 由公开模块能力读取的必要事实；员工状态与部门角色归属不可省略. */
  record Facts(
      boolean employeeActive,
      boolean departmentActive,
      boolean systemAvailable,
      boolean assigned,
      boolean roleActive,
      boolean roleBelongs,
      Set<String> activePermissions) {}

  /** 纯计算结果，便于独立测试拒绝条件. */
  record Result(Set<String> permissions, String reason) {}

  private EffectivePermissionRule() {}

  static Result evaluate(Facts facts) {
    // 先检查身份与系统范围，再解释角色，不允许空角色回退到默认权限。
    if (!facts.employeeActive()) {
      return denied("EMPLOYEE_DISABLED");
    }
    if (!facts.departmentActive()) {
      return denied("DEPARTMENT_UNAVAILABLE");
    }
    if (!facts.systemAvailable()) {
      return denied("SYSTEM_UNAVAILABLE");
    }
    if (!facts.assigned()) {
      return denied("NO_ROLE");
    }
    if (!facts.roleActive() || !facts.roleBelongs()) {
      return denied("ROLE_UNAVAILABLE");
    }
    return new Result(Set.copyOf(facts.activePermissions()), "EFFECTIVE");
  }

  private static Result denied(String reason) {
    return new Result(Set.of(), reason);
  }
}
