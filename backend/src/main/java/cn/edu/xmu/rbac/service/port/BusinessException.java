package cn.edu.xmu.rbac.service.port;

/** 业务失败状态与对外错误码. */
public class BusinessException extends RuntimeException {
  private final int status;
  private final String code;

  /** 创建包含 HTTP 状态和业务错误码的异常. */
  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static BusinessException forbidden() {
    return new BusinessException(403, "FORBIDDEN", "无操作权限");
  }

  public static BusinessException missing() {
    return new BusinessException(404, "NOT_FOUND", "对象不存在");
  }
}
