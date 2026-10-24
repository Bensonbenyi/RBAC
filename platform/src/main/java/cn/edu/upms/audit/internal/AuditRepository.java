package cn.edu.upms.audit.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 审计事件存储，支持 Service 构造的范围、时间和类型组合条件.
 *
 * <p>JpaSpecificationExecutor 只执行传入的条件，不会自动补部门权限；入口须先取得可信范围。 saveAndFlush 可提前暴露 SQL
 * 写入失败，但是否最终提交仍取决于调用事务.
 */
interface AuditRepository
    extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {}
