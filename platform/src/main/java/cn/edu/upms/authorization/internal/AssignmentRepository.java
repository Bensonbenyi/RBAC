package cn.edu.upms.authorization.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 授权模块自身分配存储；写入前由组织能力锁定员工. */
interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {
  Optional<AssignmentEntity> findByEmployeeIdAndSystemId(long employeeId, long systemId);

  List<AssignmentEntity> findByEmployeeId(long employeeId);

  List<AssignmentEntity> findByRoleIdAndRevokedFalse(long roleId);
}
