package cn.edu.upms.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** 仅由审计模块访问的事件记录. */
@Entity
class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  Instant occurredAt;
  Long actorId;
  Long departmentId;
  Long previousDepartmentId;
  Long systemId;
  String eventType;
  String objectRef;
  String result;

  @Column(columnDefinition = "TEXT")
  String detail;

  protected AuditEvent() {}
}
