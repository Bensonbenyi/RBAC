package cn.edu.upms.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把进入 MVC Controller 后的已知异常转换成 HTTP ProblemDetail.
 *
 * <p>BusinessException 带稳定原因代码和中文提示，唯一键等约束冲突映射为 409，查无对象为 404。数据库堆栈和凭据不交给前端。安全过滤器发生在 Controller
 * 之前，401/403 可能由安全链直接返回，不能假定所有错误都一定经过本类或拥有完全相同的响应结构.
 */
@RestControllerAdvice
public class FailureAdvice {
  /** 不存在的业务对象是可识别的输入错误，不泄露底层查询信息. */
  @ExceptionHandler(java.util.NoSuchElementException.class)
  public ProblemDetail missing(java.util.NoSuchElementException failure) {
    return business(new BusinessException(404, "NOT_FOUND", "对象不存在，请刷新资料"));
  }

  /** 输出可用于界面提示的稳定代码，不泄露数据库或认证资料. */
  @ExceptionHandler(BusinessException.class)
  public ProblemDetail business(BusinessException failure) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(failure.getStatus()), failure.getMessage());
    detail.setProperty("code", failure.getCode());
    return detail;
  }

  /** 数据库唯一键是并发下的最后约束，不以底层 SQL 错误提示用户. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail duplicate(DataIntegrityViolationException failure) {
    return business(BusinessException.conflict("资料重复或仍有关联对象，请刷新后检查"));
  }

  /** Bean Validation 校验输入形式；服务用例另行校验业务范围. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail invalid(MethodArgumentNotValidException failure) {
    return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(400), "输入不完整或格式错误，请检查表单");
  }
}
