package cn.edu.upms.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.shared.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 故障闸门只有当前轮探测成功才恢复，覆盖并发失败不能被旧探测结果覆盖的情形. */
class AuditServiceTest {
  @Test
  @DisplayName("ACC-F-017 审计失败暂停受控请求，恢复记录诚实报告缺口")
  void recoveryAndCounters() {
    var repository = mock(AuditRepository.class);
    var service = new AuditService(repository);
    service.requireAvailable();
    assertThat(service.statistics()).containsEntry("gapStarted", "");
    service.count("ALLOW", 10);
    service.count("DENY", 20);
    service.count("ERROR", 30);
    assertThat(service.statistics())
        .containsEntry("allowed", 1L)
        .containsEntry("denied", 1L)
        .containsEntry("errors", 1L)
        .containsEntry("elapsedNanos", 60L);
    when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("存储离线"));
    service.probeStorage();
    assertThat(service.statistics()).containsEntry("paused", true);
    String gap = service.statistics().get("gapStarted").toString();
    service.pause();
    assertThat(service.statistics()).containsEntry("gapStarted", gap);
    assertThatThrownBy(service::requireAvailable).isInstanceOf(BusinessException.class);
    doAnswer(
            call -> {
              service.pause();
              return call.getArgument(0);
            })
        .when(repository)
        .saveAndFlush(any());
    service.probeStorage();
    assertThat(service.statistics()).containsEntry("paused", true);
    doAnswer(call -> call.getArgument(0)).when(repository).saveAndFlush(any());
    service.probeStorage();
    assertThat(service.statistics()).containsEntry("paused", false);
    service.probeStorage();
    service.record(new AuditApi.Event(1L, 1L, null, 1L, "CHANGED", "employee:1", "SUCCESS", "变更"));
    assertThat(service.statistics().get("gapStarted")).isEqualTo(gap);
  }

  @Test
  @DisplayName("ACC-F-017 成功审计保存事件语义，写入异常返回技术不可用")
  void eventFieldsAndFailure() {
    var repository = mock(AuditRepository.class);
    var service = new AuditService(repository);
    service.record(new AuditApi.Event(1L, 2L, 3L, 4L, "EVENT", "object", "SUCCESS", "说明"));
    var saved = ArgumentCaptor.forClass(AuditEvent.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().actorId).isEqualTo(1);
    assertThat(saved.getValue().previousDepartmentId).isEqualTo(3);
    assertThat(saved.getValue().detail).isEqualTo("说明");
    when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("写入失败"));
    assertThatThrownBy(
            () ->
                service.record(
                    new AuditApi.Event(null, null, null, null, "EVENT", "object", "FAILURE", "说明")))
        .isInstanceOf(BusinessException.class);
    assertThat(service.statistics()).containsEntry("paused", true);
  }
}
