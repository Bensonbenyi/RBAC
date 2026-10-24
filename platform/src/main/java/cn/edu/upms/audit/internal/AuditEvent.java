package cn.edu.upms.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * 关键事件发生时的历史记录，只由审计模块保存和查询.
 *
 * <p>actorId、部门、原部门和 systemId 保存事件时的编号语义，本表没有为它们声明物理外键。历史查询不按员工今天的归属重写旧事件；调动同时保留新旧部门。对象引用是解释字符串，
 * 不能由它获得访问权。detail 禁止写入手机号、凭据、访问令牌或内部堆栈.
 */
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
