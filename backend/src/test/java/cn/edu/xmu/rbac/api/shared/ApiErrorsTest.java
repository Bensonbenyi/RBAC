package cn.edu.xmu.rbac.api.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

class ApiErrorsTest {
  private final ApiErrors errors = new ApiErrors();

  @Test
  void mapsStorageOutagesToSystemSpecificErrors() {
    var identity = errors.unavailable(new MockHttpServletRequest("GET", "/auth/me"));
    assertEquals(503, identity.getStatusCode().value());
    assertEquals("IDENTITY_SERVICE_UNAVAILABLE", ((Map<?, ?>) identity.getBody()).get("code"));
    var roles = errors.unavailable(new MockHttpServletRequest("GET", "/api/roles"));
    assertEquals(503, roles.getStatusCode().value());
    assertEquals("SERVICE_UNAVAILABLE", ((Map<?, ?>) roles.getBody()).get("code"));
  }

  @Test
  void reportsMissingParametersAndSuppressesInternalExceptionDetails() {
    var missing = errors.http(new MissingServletRequestParameterException("name", "String"));
    assertEquals(400, missing.getStatusCode().value());
    assertEquals("INVALID_ARGUMENT", ((Map<?, ?>) missing.getBody()).get("code"));
    var failure = errors.unexpected(new IllegalStateException("internal details"));
    assertEquals(500, failure.getStatusCode().value());
    assertEquals(Map.of("code", "INTERNAL_ERROR", "message", "服务处理失败"), failure.getBody());
  }
}
