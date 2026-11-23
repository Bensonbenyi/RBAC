package cn.edu.xmu.rbac.api.identity;

import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.DepartmentUserVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.DepartmentVO;
import cn.edu.xmu.rbac.api.auth.vo.IdentityVOs.PageVO;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.IdentityDirectoryService;
import cn.edu.xmu.rbac.service.port.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一身份的部门树与成员目录. */
@RestController
@Validated
@RequestMapping("/auth/departments")
public class IdentityDepartmentController {
  private final IdentityDirectoryService service;
  private final CurrentIdentity current;

  /**
   * 注入目录服务和当前操作者.
   *
   * @param service 处理当前资源用例的服务
   * @param current 读取请求中已认证身份的组件
   */
  public IdentityDepartmentController(IdentityDirectoryService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /**
   * 分页读取根部门或所选部门的直接子部门.
   *
   * @param parentId 父部门编号，根层使用 null
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param pageSize 每页最多返回的记录数
   * @return 从第 1 页计数的部门目录
   */
  @GetMapping
  public PageVO<DepartmentVO> list(
      @RequestParam(required = false) @Positive Long parentId,
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "1") @Positive int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize) {
    Data.Page<Data.DirectoryDepartment> result =
        service.departments(current.get(), parentId, keyword, page, pageSize);
    List<DepartmentVO> items =
        result.items().stream()
            .map(
                department ->
                    new DepartmentVO(
                        department.id(),
                        department.code(),
                        department.name(),
                        department.parentId(),
                        department.hasChildren()))
            .toList();
    return new PageVO<>(page, pageSize, result.total(), items);
  }

  /**
   * 分页读取指定部门的直接成员.
   *
   * @param departmentId 所属部门编号
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param pageSize 每页最多返回的记录数
   * @return 从第 1 页计数的成员目录
   */
  @GetMapping("/{departmentId}/users")
  public PageVO<DepartmentUserVO> users(
      @PathVariable @Positive Long departmentId,
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "1") @Positive int page,
      @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize) {
    Data.Page<Data.DirectoryUser> result =
        service.users(current.get(), departmentId, keyword, page, pageSize);
    List<DepartmentUserVO> items =
        result.items().stream()
            .map(
                user ->
                    new DepartmentUserVO(
                        user.userId(),
                        user.no(),
                        user.name(),
                        user.departmentId(),
                        user.departmentName()))
            .toList();
    return new PageVO<>(page, pageSize, result.total(), items);
  }
}
