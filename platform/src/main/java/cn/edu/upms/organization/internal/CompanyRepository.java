package cn.edu.upms.organization.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 公司存储及治理锁入口，由 Spring Data 自动生成实现.
 *
 * <p>JpaRepository 提供基础 CRUD，@Query 写的是实体名和属性组成的 JPQL，非数据库 SQL。 PESSIMISTIC_WRITE
 * 在当前数据库事务中取得排他行锁，事务结束后释放.
 */
interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CompanyEntity c where c.id = :id")
  Optional<CompanyEntity> lock(@Param("id") long id);
}
