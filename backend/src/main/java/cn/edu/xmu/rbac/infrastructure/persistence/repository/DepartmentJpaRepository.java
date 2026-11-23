package cn.edu.xmu.rbac.infrastructure.persistence.repository;

import cn.edu.xmu.rbac.infrastructure.persistence.entity.DepartmentEntity;
import cn.edu.xmu.rbac.service.port.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA 的部门仓储. */
public interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, Long> {

  /**
   * 按关键词分页查询统一身份目录.
   *
   * @param parentId 父部门编号，根层使用 null
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param pageable JPA 分页及排序条件
   * @return 包含当前页记录及总数的分页结果
   */
  @Query(
      value =
          """
          select new cn.edu.xmu.rbac.service.port.Data$DirectoryDepartment(
              d.id, concat('D', cast(d.id as String)), d.name, d.parentId,
              case when count(child.id) > 0 then true else false end)
          from DepartmentEntity d
          left join DepartmentEntity child on child.parentId = d.id
          where ((:parentId is null and d.parentId is null) or d.parentId = :parentId)
            and (locate(:keyword, d.name) > 0
                 or locate(:keyword, concat('D', cast(d.id as String))) > 0)
          group by d.id, d.name, d.parentId
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
