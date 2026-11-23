package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.edu.xmu.rbac.infrastructure.security.BearerFilter;
import cn.edu.xmu.rbac.infrastructure.security.CurrentIdentity;
import cn.edu.xmu.rbac.service.auth.AuthService;
import cn.edu.xmu.rbac.service.auth.OperationPermissions;
import cn.edu.xmu.rbac.service.auth.SelectionService;
import cn.edu.xmu.rbac.service.auth.UiPermissions;
import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.EffectiveAccessStore;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.port.SessionStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class SecurityBoundaryTest {
  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final SessionStore sessions = mock(SessionStore.class);
  private final ManagementStore store = mock(ManagementStore.class);
  private final EffectiveAccessStore permissions = mock(EffectiveAccessStore.class);
  private final AccessPolicy access = new AccessPolicy(permissions);
  private final BearerFilter bearer = new BearerFilter(decoder, sessions, store, permissions);
  private final Data.Identity actor = new Data.Identity(1L, 2L, "rbac", "session");
  private final Instant expires = Instant.now().plusSeconds(120);

  @Test
  void rejectsCredentialsWithIncompleteClaims() throws Exception {
    assertStatus(401, "/auth/me", "Basic invalid");
    when(decoder.decode("signed")).thenThrow(new JwtException("bad signature"));
    assertStatus(401, "/auth/me", "Bearer signed");
    Jwt jwt = mock(Jwt.class);
    doReturn(jwt).when(decoder).decode("signed");
    when(jwt.getAudience()).thenReturn(List.of());
    assertStatus(401, "/auth/me", "Bearer signed");
    when(jwt.getAudience()).thenReturn(List.of("rbac"));
    assertStatus(401, "/auth/me", "Bearer signed");
    when(jwt.getId()).thenReturn("session");
    assertStatus(401, "/auth/me", "Bearer signed");
  }

  @Test
  void rejectsMissingRevokedExpiredOrMismatchedSessionAndMissingUser() throws Exception {
    validClaims();
    assertStatus(401, "/auth/me", "Bearer signed");
    when(sessions.find("session"))
        .thenReturn(new SessionStore.Session("session", 1L, expires, Instant.now()));
    assertStatus(401, "/auth/me", "Bearer signed");
    when(sessions.find("session"))
        .thenReturn(new SessionStore.Session("session", 1L, Instant.EPOCH, null));
    assertStatus(401, "/auth/me", "Bearer signed");
    when(sessions.find("session"))
        .thenReturn(new SessionStore.Session("session", 99L, expires, null));
    assertStatus(401, "/auth/me", "Bearer signed");
    when(sessions.find("session"))
        .thenReturn(new SessionStore.Session("session", 1L, expires, null));
    when(store.user(1L)).thenThrow(BusinessException.missing());
    assertStatus(401, "/auth/me", "Bearer signed");
  }

  @Test
  void classifiesIdentityAndManagementStorageFailures() throws Exception {
    validClaims();
    when(sessions.find("session")).thenThrow(new DataAccessResourceFailureException("offline"));
    assertTrue(
        assertStatus(503, "/auth/me", "Bearer signed")
            .getContentAsString()
            .contains("IDENTITY_SERVICE_UNAVAILABLE"));
    assertTrue(
        assertStatus(503, "/api/roles", "Bearer signed")
            .getContentAsString()
            .contains("SERVICE_UNAVAILABLE"));
    assertStatus(200, "/actuator/health", null);
  }

  @Test
  void rejectsAnonymousPrincipalsAndUsersWithoutDepartment() {
    var current = new CurrentIdentity();
    assertThrows(BusinessException.class, current::get);
    try {
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", null));
      assertThrows(BusinessException.class, current::get);
    } finally {
      SecurityContextHolder.clearContext();
    }
    var noDepartment = new Data.Identity(1L, null, "rbac", "session");
    assertThrows(BusinessException.class, () -> access.scope(noDepartment, 2L));
  }

  @Test
  void usesOnlyCurrentSystemPermissionsForAuthorizationOptions() {
    final var auth = new AuthService(store, permissions, sessions, access, null, null, null);
    final var selection = new SelectionService(store, access);
    assertEquals(false, UiPermissions.contains("unknown", "ui.users"));
    assertNull(OperationPermissions.code("unknown", "listTeacherCourses"));
    when(permissions.permissionsForUser(1L, "rbac")).thenReturn(List.of("role.manage"));
    assertTrue(auth.apiAllowed(actor, "system.options"));
    when(permissions.permissionsForUser(1L, "rbac")).thenReturn(List.of("permission.manage"));
    assertTrue(auth.apiAllowed(actor, "system.options"));
    var page = new Data.Page<Data.System>(List.of(), 0, 20, 0);
    when(store.systems(0, 20)).thenReturn(page);
    assertEquals(page, selection.systems(actor, 0, 20));
    when(permissions.permissionsForUser(1L, "rbac")).thenReturn(List.of());
    assertEquals(false, auth.apiAllowed(actor, "system.options"));
    assertThrows(BusinessException.class, () -> selection.systems(actor, 0, 20));
  }

  @Test
  void checksLoginGrantsOnEveryRequestAndStillAllowsLogout() throws Exception {
    validClaims();
    when(sessions.find("session"))
        .thenReturn(new SessionStore.Session("session", 1L, expires, null));
    when(store.user(1L)).thenReturn(new Data.User(1L, "demo", "测试人员", "13800000000", 2L));
    assertStatus(403, "/auth/me", "Bearer signed");
    assertStatus(403, "/auth/logout", "Bearer signed");
    var request = new MockHttpServletRequest("POST", "/auth/logout");
    request.addHeader("Authorization", "Bearer signed");
    var response = new MockHttpServletResponse();
    bearer.doFilter(
        request, response, (req, res) -> assertEquals(actor, new CurrentIdentity().get()));
    assertEquals(200, response.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void projectsCourseRolesWithoutLeakingLocalRoleNames() {
    final var auth = new AuthService(store, permissions, sessions, access, null, null, null);
    when(store.user(1L)).thenReturn(new Data.User(1L, "demo", "测试人员", "13800000000", 2L));
    when(permissions.rolesForUser(1L))
        .thenReturn(List.of("admin", "reviewer", "viewer", "student", "local-role"));
    var course = new Data.Identity(1L, 2L, "course-project-management", "session");
    assertEquals(List.of("admin", "teacher", "student"), auth.me(course).roles());
  }

  private void validClaims() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getAudience()).thenReturn(List.of("rbac"));
    when(jwt.getId()).thenReturn("session");
    when(jwt.getExpiresAt()).thenReturn(expires);
    when(jwt.getSubject()).thenReturn("1");
    when(decoder.decode("signed")).thenReturn(jwt);
  }

  private MockHttpServletResponse assertStatus(int status, String path, String authorization)
      throws Exception {
    var request = new MockHttpServletRequest("GET", path);
    var response = new MockHttpServletResponse();
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    bearer.doFilter(request, response, (req, res) -> assertEquals(200, status));
    assertEquals(status, response.getStatus(), response.getContentAsString());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    return response;
  }
}
