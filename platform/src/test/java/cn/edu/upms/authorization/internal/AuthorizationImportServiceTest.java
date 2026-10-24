package cn.edu.upms.authorization.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.authorization.api.AuthorizationImportApi;
import cn.edu.upms.shared.BusinessException;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

/** 导入映射验证列顺序和参数化绑定，禁止再次装载覆盖已有角色分配. */
class AuthorizationImportServiceTest {
  @Test
  @DisplayName("压测分配只进入空表，参数绑定保留员工、系统和角色编号")
  void assignmentBindings() throws Exception {
    var repository = mock(AssignmentRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var statement = mock(PreparedStatement.class);
    doAnswer(
            call -> {
              @SuppressWarnings("unchecked")
              var setter =
                  (ParameterizedPreparedStatementSetter<AuthorizationImportApi.Row>)
                      call.getArgument(3);
              setter.setValues(statement, new AuthorizationImportApi.Row(10, 20, 30));
              return new int[][] {{1}};
            })
        .when(jdbc)
        .batchUpdate(anyString(), any(Collection.class), anyInt(), any());
    var service = new AuthorizationImportService(repository, jdbc);
    service.load(List.of(new AuthorizationImportApi.Row(10, 20, 30)));
    verify(statement).setLong(1, 10);
    verify(statement).setLong(2, 20);
    verify(statement).setLong(3, 30);
    verify(statement).setTimestamp(org.mockito.ArgumentMatchers.eq(4), any());
    when(repository.count()).thenReturn(1L);
    assertThatThrownBy(() -> service.load(List.of())).isInstanceOf(BusinessException.class);
  }
}
