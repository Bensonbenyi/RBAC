package cn.edu.upms.organization.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 空员工表不代表空组织库，已有公司同样必须阻止初始化覆盖. */
class OrganizationImportServiceTest {
  @Test
  @DisplayName("已有公司但尚无员工时拒绝基础导入，不发出任何 INSERT")
  void companiesWithoutEmployees() {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbc.queryForObject("SELECT COUNT(*) FROM company", Long.class)).thenReturn(1L);
    assertThatThrownBy(
            () -> new OrganizationImportService(jdbc).load(List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalStateException.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
        .queryForObject("SELECT COUNT(*) FROM company", Long.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
        .queryForObject("SELECT COUNT(*) FROM department", Long.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
        .queryForObject("SELECT COUNT(*) FROM employee", Long.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2))
        .queryForObject("SELECT COUNT(*) FROM employee WHERE department_id IS NULL", Long.class);
    verifyNoMoreInteractions(jdbc);
  }
}
