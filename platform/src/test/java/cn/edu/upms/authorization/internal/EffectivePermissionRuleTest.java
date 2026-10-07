package cn.edu.upms.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 穷举身份、部门、系统、分配和角色条件，验证缺少任一事实均不允许. */
class EffectivePermissionRuleTest {
  @Test
  @DisplayName("ACC-F-012 有效权限要求所有状态和归属条件同时成立")
  void allRequiredFacts() {
    for (int mask = 0; mask < 64; mask++) {
      var facts =
          new EffectivePermissionRule.Facts(
              bit(mask, 0),
              bit(mask, 1),
              bit(mask, 2),
              bit(mask, 3),
              bit(mask, 4),
              bit(mask, 5),
              Set.of("document:read"));
      var result = EffectivePermissionRule.evaluate(facts);
      assertThat(result.permissions().contains("document:read"))
          .as("事实组合 %s", mask)
          .isEqualTo(mask == 63);
    }
  }

  private static boolean bit(int mask, int index) {
    return (mask & (1 << index)) != 0;
  }
}
