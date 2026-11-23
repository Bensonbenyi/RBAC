package cn.edu.xmu.rbac.api.shared;

import cn.edu.xmu.rbac.service.port.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 统一映射 MVC 层异常及公开错误响应. */
@RestControllerAdvice
public class ApiErrors {

  /**
   * 构造统一错误响应.
   *
   * @param status 对外返回的 HTTP 状态码
   * @param code 提供给客户端的业务错误码
   * @param message 提供给调用方的错误说明
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  private ResponseEntity<Map<String, String>> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
  }

  /**
   * 将业务异常转成对应的 HTTP 状态和错误码.
   *
   * @param e 待映射的原始异常
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return error(e.status(), e.code(), e.getMessage());
  }

  /**
   * 将请求参数错误转换为对应系统的 400 响应.
   *
   * @param request 用于区分接口路径的 HTTP 请求
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(HttpServletRequest request) {
    String code = request.getRequestURI().startsWith("/auth/") ? "BAD_REQUEST" : "INVALID_ARGUMENT";
    return error(400, code, "参数格式或字段不符合要求");
  }

  /**
   * 将数据完整性冲突映射成 HTTP 409.
   *
   * @param e 待映射的原始异常
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return error(409, "CONFLICT", "存在关联或重复数据");
  }

  /**
   * 将数据库不可用映射成对应系统的 HTTP 503.
   *
   * @param request 用于区分接口路径的 HTTP 请求
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<?> unavailable(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/auth/")) {
      return error(503, "IDENTITY_SERVICE_UNAVAILABLE", "统一身份认证暂时不可用");
    }
    return error(503, "SERVICE_UNAVAILABLE", "数据服务暂不可用");
  }

  /**
   * 保留 Web 框架给出的 HTTP 状态及标准响应头.
   *
   * @param e 待映射的原始异常
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.HttpRequestMethodNotSupportedException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<?> http(org.springframework.web.ErrorResponse e) {
    int status = e.getStatusCode().value();
    String code =
        status == 404 ? "NOT_FOUND" : status == 405 ? "METHOD_NOT_ALLOWED" : "INVALID_ARGUMENT";
    return ResponseEntity.status(status)
        .headers(e.getHeaders())
        .body(Map.of("code", code, "message", "请求路径、方法或必要参数不符合接口定义"));
  }

  /**
   * 返回统一的 HTTP 500 响应.
   *
   * @param e 待映射的原始异常
   * @return 携带业务错误码和消息的 HTTP 响应
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    return error(500, "INTERNAL_ERROR", "服务处理失败");
  }
}
