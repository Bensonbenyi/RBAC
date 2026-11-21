package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.edu.xmu.rbac.service.ddd.domain.User;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void userAggregateRequiresRoleInSameDepartment() {
    var user = new User(new Data.User(1L, "one", "姓名", "13800000000", 10L));
    user.requireRoleInDepartment(new Data.Role(20L, "角色", 10L));
    assertThrows(
        BusinessException.class,
        () -> user.requireRoleInDepartment(new Data.Role(30L, "外部门", 11L)));
  }

  @Test
  void profileChangePreservesUnspecifiedFields() {
    var user = new User(new Data.User(1L, "one", "姓名", "13800000000", 10L));
    user.changeProfile(new Data.Profile("新姓名", null));
    assertEquals("13800000000", user.profile().phone());
  }
}
