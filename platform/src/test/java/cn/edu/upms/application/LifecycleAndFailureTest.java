package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.identity.api.IdentityApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import cn.edu.upms.shared.FailureAdvice;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 明确区分提交前数据库工作和提交后内存副作用，错误响应不泄露底层信息. */
class LifecycleAndFailureTest {
  @Test
  @DisplayName("ACC-F-014 版本冲突不撤会话，停用成功提交后才撤会话，启用不恢复旧授权")
  void stateBoundary() {
    var organization = mock(OrganizationApi.class);
    var authorization = mock(AuthorizationApi.class);
    var identity = mock(IdentityApi.class);
    var audit = mock(AuditApi.class);
    var lifecycle = new EmployeeLifecycle(organization, authorization, identity, audit);
    var employee =
        new OrganizationApi.Employee(1L, "a", "员工", "p", 1L, 1L, "岗位", true, false, true, 0);
    when(organization.lockEmployee(1)).thenReturn(employee);
    when(organization.employee(1)).thenReturn(employee);
    when(organization.administrator(2)).thenReturn(employee);
    assertThatThrownBy(() -> lifecycle.state(2, 1, false, 1)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> lifecycle.transfer(2, 1, 2, 0)).isInstanceOf(BusinessException.class);
    TransactionSynchronizationManager.initSynchronization();
    try {
      lifecycle.state(2, 1, false, 0);
      verify(identity, never()).revokeEmployeeSessions(1);
      verify(authorization).revokeAll(2, 1);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);
      verify(identity).revokeEmployeeSessions(1);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    assertThat(lifecycle.state(2, 1, true, 0)).isEqualTo(employee);
  }

  @Test
  @DisplayName("不存在对象与唯一约束失败分别输出稳定错误，不泄露 SQL")
  void stableErrors() {
    var advice = new FailureAdvice();
    assertThat(advice.missing(new NoSuchElementException("内部路径")).getStatus()).isEqualTo(404);
    var duplicate = advice.duplicate(new DataIntegrityViolationException("敏感 SQL"));
    assertThat(duplicate.getStatus()).isEqualTo(409);
    assertThat(duplicate.getDetail()).doesNotContain("SQL");
  }
}
