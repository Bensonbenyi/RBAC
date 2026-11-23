package cn.edu.xmu.rbac.service.port;

import java.util.List;

/** 双实现共享的不可变数据契约. */
public final class Data {
  private Data() {}

  /** 用户账号及其部门归属. */
  public record User(Long id, String username, String name, String phone, Long departmentId) {}

  /** 部门领域对象，维护名称. */
  public record Department(Long id, String name, Long parentId) {}

  /** 身份目录节点，包含展开提示. */
  public record DirectoryDepartment(
      Long id, String code, String name, Long parentId, boolean hasChildren) {}

  /** 身份目录用户，仅提供工号、姓名及所属部门. */
  public record DirectoryUser(
      String userId, String no, String name, Long departmentId, String departmentName) {}

  /** 业务系统领域对象，维护名称. */
  public record System(Long id, String systemKey, String name) {}

  /** 角色基本资料及部门归属. */
  public record Role(Long id, String name, Long departmentId) {}

  /** 权限领域对象，维护名称. */
  public record Permission(Long id, Long systemId, String code, String name) {}

  /** Name 数据契约. */
  public record Name(String name) {}

  /** Page 数据契约. */
  public record Page<T>(List<T> items, int page, int size, long total) {}

  /** Identity 数据契约. */
  public record Identity(Long userId, Long departmentId, String systemKey, String sessionId) {}
}
