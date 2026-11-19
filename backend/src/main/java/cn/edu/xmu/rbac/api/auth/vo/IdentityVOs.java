package cn.edu.xmu.rbac.api.auth.vo;

import java.util.List;

/** 统一身份接口的响应字段. */
public final class IdentityVOs {
  private IdentityVOs() {}

  /** 签发结果，有效期以秒为单位. */
  public record LoginVO(String accessToken, String tokenType, long expiresIn) {}

  /** 当前身份、工号、角色与目标系统的界面权限. */
  public record CurrentUserVO(
      String subject,
      String no,
      String name,
      Long departmentId,
      List<String> roles,
      List<String> permissions) {}

  /** 查询身份目录时可公开的用户字段. */
  public record UserVO(String userId, String no, String name) {}

  /** 部门树的单层节点，hasChildren 表示可继续展开. */
  public record DepartmentVO(
      Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /** 候选人身份及所属部门. */
  public record DepartmentUserVO(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /** 从第一页开始的身份目录分页. */
  public record PageVO<T>(int page, int pageSize, long total, List<T> items) {}

  /** 权限决策仅返回是否获准. */
  public record AllowedVO(boolean allowed) {}
}
