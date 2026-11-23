package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.UserEntity;
import cn.edu.xmu.rbac.service.port.Data;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的用户账号仓储. */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

  /**
   * 按唯一用户名查找账号实体.
   *
   * @param username 唯一登录账号
   * @return 存在时包含账号实体的可选值
   */
  Optional<UserEntity> findByUsername(String username);

  /**
   * 按部门筛选持久化记录.
   *
   * @param departmentId 所属部门编号
   * @param pageable JPA 分页及排序条件
   * @return 包含当前页记录及总数的分页结果
   */
  Page<UserEntity> findByDepartmentId(Long departmentId, Pageable pageable);

  /**
   * 按关键词分页查询统一身份目录.
   *
   * @param departmentId 所属部门编号
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param pageable JPA 分页及排序条件
   * @return 包含当前页记录及总数的分页结果
   */
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
