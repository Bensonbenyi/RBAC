package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.edu.xmu.rbac.service.ddd.domain.Role;
import cn.edu.xmu.rbac.service.ddd.domain.User;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void userAggregateEnforcesRoleDepartmentAndDuplicateAssignment() {
    var user = new User(new Data.User(1L, "one", "姓名", "13800000000", 10L));
    var role = new Data.Role(20L, "角色", 10L);
    user.assignRole(role, 10L);
    assertTrue(user.roleIds().contains(20L));
    assertThrows(BusinessException.class, () -> user.assignRole(role, 10L));
    assertThrows(
        BusinessException.class, () -> user.assignRole(new Data.Role(30L, "外部门", 11L), 10L));
    user.revokeRole(20L);
    assertTrue(user.roleIds().isEmpty());
  }

  @Test
  void profileChangePreservesUnspecifiedFields() {
    var user = new User(new Data.User(1L, "one", "姓名", "13800000000", 10L));
    user.changeProfile(new Data.Profile("新姓名", null));
    assertEquals("13800000000", user.profile().phone());
  }

  @Test
  void rolePermissionCollectionIsIdempotentAndImmutable() {
    var role = new Role(new Data.Role(20L, "角色", 10L));
    var permission = new Data.Permission(1L, 2L, "CR", "审核");
    role.grantPermission(permission);
    role.grantPermission(permission);
    assertEquals(1, role.permissionIds().size());
    assertThrows(UnsupportedOperationException.class, () -> role.permissionIds().clear());
    role.revokePermission(1L);
    assertTrue(role.permissionIds().isEmpty());
  }
}
