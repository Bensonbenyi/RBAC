package cn.edu.xmu.rbac.service.port;

/** 业务失败状态与对外错误码. */
public class BusinessException extends RuntimeException {
  private final int status;
  private final String code;

  /**
   * 创建包含 HTTP 状态和业务错误码的异常.
   *
   * @param status 对外返回的 HTTP 状态码
   * @param code 提供给客户端的业务错误码
   * @param message 提供给调用方的错误说明
   */
  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  /**
   * 读取异常对应的 HTTP 状态码.
   *
   * @return HTTP 状态码
   */
  public int status() {
    return status;
  }

  /**
   * 读取业务错误码.
   *
   * @return 对应的业务错误码
   */
  public String code() {
    return code;
  }

  /**
   * 创建无操作权限的异常.
   *
   * @return 对应业务失败原因的异常
   */
  public static BusinessException forbidden() {
    return new BusinessException(403, "FORBIDDEN", "无操作权限");
  }

  /**
   * 创建对象不存在的异常.
   *
   * @return 对应业务失败原因的异常
   */
  public static BusinessException missing() {
    return new BusinessException(404, "NOT_FOUND", "对象不存在");
  }
}
