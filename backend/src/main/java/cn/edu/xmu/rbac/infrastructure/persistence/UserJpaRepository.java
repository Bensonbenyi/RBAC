package cn.edu.xmu.rbac.infrastructure.persistence;

import cn.edu.xmu.rbac.service.port.Data;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByUsername(String username);

  Page<UserEntity> findByDepartmentId(Long departmentId, Pageable pageable);

  @Query(
      value =
          """
          select new cn.edu.xmu.rbac.service.port.Data$DirectoryUser(
              cast(u.id as String), u.username, u.name, u.departmentId, d.name)
          from UserEntity u join DepartmentEntity d on d.id = u.departmentId
          where u.departmentId = :departmentId
            and (locate(:keyword, u.name) > 0 or locate(:keyword, u.username) > 0)
          order by u.id
          """,
      countQuery =
          """
          select count(u) from UserEntity u
          where u.departmentId = :departmentId
            and (locate(:keyword, u.name) > 0 or locate(:keyword, u.username) > 0)
          """)
  Page<Data.DirectoryUser> directory(Long departmentId, String keyword, Pageable pageable);
}
