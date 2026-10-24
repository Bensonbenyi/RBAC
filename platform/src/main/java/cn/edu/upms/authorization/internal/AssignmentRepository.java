package cn.edu.upms.authorization.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 角色分配存储；每员工每系统查找对应数据库唯一键.
 *
 * <p>授权写入先通过组织公开能力锁员工；仅使用先查后存无法阻止并发首次插入。角色影响查询保留所有未撤销分配，即使角色已停用，也须在删除前由管理员显式撤权.
 */
interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {
  Optional<AssignmentEntity> findByEmployeeIdAndSystemId(long employeeId, long systemId);

  List<AssignmentEntity> findByEmployeeId(long employeeId);

  List<AssignmentEntity> findByRoleIdAndRevokedFalse(long roleId);
}
