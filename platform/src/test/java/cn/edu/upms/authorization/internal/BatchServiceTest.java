package cn.edu.upms.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.upms.audit.api.AuditApi;
import cn.edu.upms.authorization.api.AuthorizationApi;
import cn.edu.upms.authorization.api.BatchApi;
import cn.edu.upms.organization.api.OrganizationApi;
import cn.edu.upms.shared.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 任务状态机与幂等边界的独立测试，真实事务部分成功由 OIDC 集成测试验证. */
class BatchServiceTest {
  final BatchRepository jobs = mock(BatchRepository.class);
  final BatchItemRepository items = mock(BatchItemRepository.class);
  final AuthorizationApi authorization = mock(AuthorizationApi.class);
  final OrganizationApi organization = mock(OrganizationApi.class);
  final AuditApi audit = mock(AuditApi.class);
  final TransactionTemplate transaction = mock(TransactionTemplate.class);
  final Map<Long, BatchEntity> savedJobs = new ConcurrentHashMap<>();
  final Map<Long, BatchItemEntity> savedItems = new ConcurrentHashMap<>();
  final AtomicLong ids = new AtomicLong();
  final BatchService service =
      new BatchService(jobs, items, authorization, organization, audit, transaction);

  @BeforeEach
  void fixtures() {
    when(transaction.execute(any()))
        .thenAnswer(
            call -> {
              TransactionCallback<?> callback = call.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
    doAnswer(
            call -> {
              Consumer<TransactionStatus> callback = call.getArgument(0);
              callback.accept(mock(TransactionStatus.class));
              return null;
            })
        .when(transaction)
        .executeWithoutResult(any());
    when(organization.administrator(anyLong()))
        .thenAnswer(call -> actor(call.getArgument(0), false));
    when(organization.administrator(1)).thenReturn(actor(1, true));
    when(organization.employee(anyLong())).thenAnswer(call -> actor(call.getArgument(0), false));
    when(jobs.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              BatchEntity job = call.getArgument(0);
              ReflectionTestUtils.setField(job, "id", ids.incrementAndGet());
              savedJobs.put(job.getId(), job);
              return job;
            });
    when(items.save(any()))
        .thenAnswer(
            call -> {
              BatchItemEntity item = call.getArgument(0);
              ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
              savedItems.put(item.getId(), item);
              return item;
            });
    when(jobs.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(savedJobs.get(call.getArgument(0))));
    when(items.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(savedItems.get(call.getArgument(0))));
    when(items.findByJobId(anyLong()))
        .thenAnswer(
            call ->
                savedItems.values().stream()
                    .filter(i -> i.jobId.equals(call.getArgument(0)))
                    .toList());
    when(jobs.findAll()).thenAnswer(call -> new ArrayList<>(savedJobs.values()));
    when(jobs.findByActorIdAndRequestKey(anyLong(), any()))
        .thenAnswer(
            call ->
                savedJobs.values().stream()
                    .filter(
                        j ->
                            j.actorId.equals(call.getArgument(0))
                                && j.requestKey.equals(call.getArgument(1)))
                    .findFirst());
  }

  @Test
  @DisplayName("ACC-F-011 请求缺失、超量、重复员工及错误幂等标识均在执行前拒绝")
  void inputBoundaries() {
    denied(() -> service.submit(1, null));
    denied(
        () ->
            service.submit(
                1, new BatchApi.Request(null, 1, null, List.of(new BatchApi.Target(1, 0)))));
    for (List<BatchApi.Target> targets :
        java.util.Arrays.asList(
            null,
            List.<BatchApi.Target>of(),
            java.util.Collections.nCopies(101, new BatchApi.Target(1, 0)),
            List.of(new BatchApi.Target(1, 0), new BatchApi.Target(1, 1)))) {
      denied(() -> service.submit(1, new BatchApi.Request("valid-key", 1, null, targets)));
    }
    denied(
        () ->
            service.submit(
                1, new BatchApi.Request("bad", 1, null, List.of(new BatchApi.Target(1, 0)))));
    assertThat(savedJobs).isEmpty();
  }

  @Test
  @DisplayName("ACC-F-011 幂等重报比较系统、角色和员工版本集合，撤权也能建立任务")
  void idempotencyAndScope() {
    var request = request(null);
    var job = service.submit(2, request);
    assertThat(job.status()).isEqualTo("COMPLETED");
    assertThat(service.submit(2, request).id()).isEqualTo(job.id());
    denied(() -> service.submit(2, new BatchApi.Request("valid-key", 2, null, request.targets())));
    denied(() -> service.submit(2, new BatchApi.Request("valid-key", 1, 1L, request.targets())));
    denied(
        () ->
            service.submit(
                2, new BatchApi.Request("valid-key", 1, null, List.of(new BatchApi.Target(1, 9)))));
    assertThat(service.jobs(1)).hasSize(1);
    assertThat(service.jobs(2)).hasSize(1);
    assertThat(service.jobs(3)).isEmpty();
    when(organization.employee(1))
        .thenReturn(
            new OrganizationApi.Employee(1L, "a", "员工", "p", 1L, 2L, "岗位", true, false, false, 0));
    assertThat(service.jobs(2)).isEmpty();
    denied(() -> service.retry(3, job.id(), List.of(new BatchApi.Target(1, 0))));
    denied(() -> service.retry(2, job.id(), List.of(new BatchApi.Target(9, 0))));
    denied(() -> service.retry(2, job.id(), List.of(new BatchApi.Target(1, 0))));
    savedItems.values().forEach(i -> i.status = "PENDING");
    denied(() -> service.retry(2, job.id(), List.of(new BatchApi.Target(1, 0))));
  }

  @Test
  @DisplayName("ACC-F-011 技术失败保存可解释状态，中断只改变待处理项，重试跳过成功项")
  void failureRecoveryAndReentry() {
    when(authorization.change(2, 1, 1, 1L, 0)).thenThrow(new IllegalStateException("连接失败"));
    var job = service.submit(2, request(1L));
    assertThat(job.status()).isEqualTo("PARTIAL_FAILURE");
    assertThat(job.items().getFirst().message()).contains("技术失败");
    var entity = savedJobs.get(job.id());
    entity.status = "RUNNING";
    var item = savedItems.values().iterator().next();
    item.status = "PENDING";
    service.markInterrupted();
    assertThat(entity.status).isEqualTo("INTERRUPTED");
    assertThat(item.status).isEqualTo("INTERRUPTED");
    service.markInterrupted();
    when(authorization.change(2, 1, 1, 1L, 1)).thenReturn(null);
    assertThat(service.retry(2, job.id(), List.of(new BatchApi.Target(1, 1))).status())
        .isEqualTo("COMPLETED");
    entity.status = "RUNNING";
    item.status = "SUCCESS";
    service.markInterrupted();
    assertThat(item.status).isEqualTo("SUCCESS");
    var failed = new BatchItemEntity();
    ReflectionTestUtils.setField(failed, "id", ids.incrementAndGet());
    failed.jobId = job.id();
    failed.employeeId = 2L;
    failed.expectedVersion = 0;
    failed.status = "FAILED";
    savedItems.put(failed.getId(), failed);
    assertThat(service.retry(2, job.id(), List.of(new BatchApi.Target(2, 1))).status())
        .isEqualTo("COMPLETED");
    @SuppressWarnings("unchecked")
    Set<Long> running = (Set<Long>) ReflectionTestUtils.getField(service, "running");
    running.add(job.id());
    denied(() -> service.retry(2, job.id(), List.of(new BatchApi.Target(2, 1))));
    running.clear();
    // 模拟另一执行者已保留下一任务编号，验证内部执行入口同样拒绝重入。
    running.add(ids.get() + 1);
    denied(
        () ->
            service.submit(
                2,
                new BatchApi.Request("another-key", 1, null, List.of(new BatchApi.Target(1, 0)))));
  }

  private static BatchApi.Request request(Long role) {
    return new BatchApi.Request("valid-key", 1, role, List.of(new BatchApi.Target(1, 0)));
  }

  private static OrganizationApi.Employee actor(long id, boolean global) {
    return new OrganizationApi.Employee(
        id, "admin", "管理员", "p", 1L, 1L, "岗位", true, global, !global, 0);
  }

  private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOf(BusinessException.class);
  }
}
