package cn.edu.xmu.rbac.service.port;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public final class Data {
  private Data() {}

  public record User(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String username,
      String name,
      String phone,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long departmentId) {}

  public record Department(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String name,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long parentId) {}

  public record System(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id, String systemKey, String name) {}

  public record Role(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      String name,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long departmentId) {}

  public record Permission(
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
      @JsonFormat(shape = JsonFormat.Shape.STRING) Long systemId,
      String code,
      String name) {}

  public record CreateUser(String username, String name, String phone) {}

  public record Profile(String name, String phone) {}

  public record Name(String name) {}

  public record CreateDepartment(String name, Long parentId) {}

  public record CreateSystem(String systemKey, String name) {}

  public record CreatePermission(String code, String name) {}

  public record Page<T>(List<T> items, int page, int size, long total) {}

  public record Identity(Long userId, Long departmentId, String systemKey, String sessionId) {}
}
