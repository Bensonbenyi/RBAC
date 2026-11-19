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

  /** 注入目录服务和当前操作者. */
  public IdentityDepartmentController(IdentityDirectoryService service, CurrentIdentity current) {
    this.service = service;
    this.current = current;
  }

  /** 从根部门开始，按上级逐层展开. */
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

  /** 查询部门下的身份候选人. */
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
