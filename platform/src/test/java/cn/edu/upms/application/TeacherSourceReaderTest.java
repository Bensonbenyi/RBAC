package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 对真实教师资料进行只读验收，避免范围标记导致漏读. */
class TeacherSourceReaderTest {
  @Test
  @DisplayName("ACC-F-001 真实来源人数、两层组织、待确认部门和初始模板准确")
  void sourceCountsAndActions() throws Exception {
    var source = TeacherSourceReader.read(Path.of("..", "docs"));
    assertThat(source.companies()).hasSize(10);
    assertThat(source.departments()).hasSize(101);
    assertThat(source.employees()).hasSize(10000);
    assertThat(source.employees().stream().filter(e -> e.departmentId() == null)).hasSize(54);
    assertThat(source.systems()).hasSize(17).noneMatch(s -> s.name().equals("系统管理后台"));
    assertThat(source.systems().stream().mapToInt(s -> s.templates().size()).sum()).isEqualTo(85);
    assertThat(source.systems().getFirst().permissions()).anyMatch(p -> p.action().equals("审批"));
    assertThat(source.systems().stream().flatMap(s -> s.permissions().stream()))
        .noneMatch(p -> Set.of("全部", "无", "view").contains(p.action()));
    assertThat(source.systems().stream().flatMap(s -> s.permissions().stream()))
        .anyMatch(p -> p.action().contains("本人"));
  }
}
