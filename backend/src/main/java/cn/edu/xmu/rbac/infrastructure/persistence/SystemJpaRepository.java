package cn.edu.xmu.rbac.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SystemJpaRepository extends JpaRepository<SystemEntity, Long> {}
