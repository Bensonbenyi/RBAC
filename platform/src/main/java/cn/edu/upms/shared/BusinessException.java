package cn.edu.upms.shared;

/** 可向调用者说明的业务失败，不携带登录凭据或内部堆栈. */
public class BusinessException extends RuntimeException {
  private final int status;
  private final String code;

  /** 保存稳定原因代码、HTTP 状态和中文处理提示. */
  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  /** 对状态变化返回冲突，要求操作者刷新而不是静默覆盖. */
  public static BusinessException conflict(String message) {
    return new BusinessException(409, "STATE_CONFLICT", message);
  }

  /** 对超出管理范围的对象统一拒绝. */
  public static BusinessException forbidden() {
    return new BusinessException(403, "OUT_OF_SCOPE", "无权访问或操作该对象");
  }

  /** 不能可靠确认时返回技术失败，调用方必须拒绝受控操作. */
  public static BusinessException unavailable() {
    return new BusinessException(503, "CONTROLLED_SERVICE_PAUSED", "受控服务暂不可用，请稍后重试");
  }
}
