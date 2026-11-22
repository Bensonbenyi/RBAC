package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA 的业务系统仓储. */
public interface SystemJpaRepository extends JpaRepository<SystemEntity, Long> {}
