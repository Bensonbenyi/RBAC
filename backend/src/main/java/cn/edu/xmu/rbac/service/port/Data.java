package cn.edu.xmu.rbac.service.port;

import cn.edu.xmu.clonefactory.CopyNotNullFrom;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/** 双实现共享的不可变数据契约. */
public final class Data {
  private Data() {}

  /** 用户聚合，维护资料及本部门角色集合. */
  public record User(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String username,
      String name,
      String phone,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long departmentId) {}

  /** 部门领域对象，维护名称. */
  public record Department(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String name,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long parentId) {}

  /** 业务系统领域对象，维护名称. */
  public record System(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id, String systemKey, String name) {}

  /** 角色聚合，维护名称和已授予权限集合. */
  public record Role(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String name,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long departmentId) {}

  /** 权限领域对象，维护名称. */
  public record Permission(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long systemId,
      String code,
      String name) {}

  /** 高级角色继承基础角色的有向边. */
  public record Inheritance(Long seniorRoleId, Long juniorRoleId) {}

  /** CreateUser 数据契约. */
  public record CreateUser(String username, String name, String phone) {}

  /** PATCH 的空字段保持原值；编译期生成不可变对象拷贝. */
  @CopyNotNullFrom({Profile.class})
  public record Profile(String name, String phone) {}

  /** Name 数据契约. */
  public record Name(String name) {}

  /** CreateDepartment 数据契约. */
  public record CreateDepartment(String name, Long parentId) {}

  /** CreateSystem 数据契约. */
  public record CreateSystem(String systemKey, String name) {}

  /** CreatePermission 数据契约. */
  public record CreatePermission(String code, String name) {}

  /** Page 数据契约. */
  public record Page<T>(List<T> items, int page, int size, long total) {}

  /** Identity 数据契约. */
  public record Identity(Long userId, Long departmentId, String systemKey, String sessionId) {}
}
