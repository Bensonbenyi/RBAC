package cn.edu.upms.audit.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 事件存储只在审计模块内部使用. */
interface AuditRepository
    extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {}
