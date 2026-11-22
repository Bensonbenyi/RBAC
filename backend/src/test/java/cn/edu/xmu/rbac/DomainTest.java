package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.edu.xmu.rbac.service.ddd.domain.Role;
import cn.edu.xmu.rbac.service.ddd.domain.User;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.Set;
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
  void roleRejectsInheritanceCycleAndDifferentDepartment() {
    var role = new Role(new Data.Role(1L, "高级角色", 10L));
    var base = new Data.Role(2L, "基础角色", 10L);
    role.requireBaseRole(base, Set.of(2L));
    assertThrows(BusinessException.class, () -> role.requireBaseRole(base, Set.of(2L, 1L)));
    assertThrows(
        BusinessException.class,
        () -> role.requireBaseRole(new Data.Role(3L, "外部门", 11L), Set.of(3L)));
  }
}
