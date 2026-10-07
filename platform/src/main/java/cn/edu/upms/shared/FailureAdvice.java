package cn.edu.upms.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将服务端拒绝转为统一错误语义，保留业务拒绝和技术失败的区别. */
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
