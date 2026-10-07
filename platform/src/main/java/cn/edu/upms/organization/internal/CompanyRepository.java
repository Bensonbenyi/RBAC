package cn.edu.upms.organization.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 公司存储和管理员变更的固定锁点. */
interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CompanyEntity c where c.id = :id")
  Optional<CompanyEntity> lock(@Param("id") long id);
}
