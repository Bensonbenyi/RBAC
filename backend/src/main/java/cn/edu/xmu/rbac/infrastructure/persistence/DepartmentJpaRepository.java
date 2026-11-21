package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from DepartmentEntity d where d.id = :id")
  Optional<DepartmentEntity> lockById(Long id);

  @Query(
      value =
          """
          select new cn.edu.xmu.rbac.service.port.Data$DirectoryDepartment(
              d.id, concat('D', cast(d.id as String)), d.name, d.parentId,
              exists (select 1 from DepartmentEntity child where child.parentId = d.id))
          from DepartmentEntity d
          where ((:parentId is null and d.parentId is null) or d.parentId = :parentId)
            and (locate(:keyword, d.name) > 0
                 or locate(:keyword, concat('D', cast(d.id as String))) > 0)
          order by d.id
          """,
      countQuery =
          """
          select count(d) from DepartmentEntity d
          where ((:parentId is null and d.parentId is null) or d.parentId = :parentId)
            and (locate(:keyword, d.name) > 0
                 or locate(:keyword, concat('D', cast(d.id as String))) > 0)
          """)
  Page<Data.DirectoryDepartment> directory(Long parentId, String keyword, Pageable pageable);
}
