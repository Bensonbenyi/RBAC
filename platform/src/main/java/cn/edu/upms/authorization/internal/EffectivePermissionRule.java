package cn.edu.upms.authorization.internal;

import java.util.Set;

/**
 * 只根据输入事实计算有效权限的纯业务规则.
 *
 * <p>本类不查询数据库、不读取当前线程身份，也不修改输入；相同事实总得到相同结果。 Service 负责收集事实和事务，本类负责按固定次序解释拒绝原因，因此可用小型单元测试逐项验证.
 *
 * <p>Facts 中的布尔值含义不同：assigned 表示存在未撤销分配，roleActive 表示角色启用， roleBelongs
 * 表示角色确实属于员工当前部门和目标系统。不能把这些条件合并成"有角色即可".
 */
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
