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

/** ApiErrors 数据契约. */
@RestControllerAdvice
public class ApiErrors {

  private ResponseEntity<Map<String, String>> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return error(e.status(), e.code(), e.getMessage());
  }

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

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return error(409, "CONFLICT", "存在关联或重复数据");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<?> unavailable(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/auth/")) {
      return error(503, "IDENTITY_SERVICE_UNAVAILABLE", "统一身份认证暂时不可用");
    }
    return error(503, "SERVICE_UNAVAILABLE", "数据服务暂不可用");
  }

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

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    return error(500, "INTERNAL_ERROR", "服务处理失败");
  }
}
