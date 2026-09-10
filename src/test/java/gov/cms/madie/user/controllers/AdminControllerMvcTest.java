package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.SecurityConfig;
import gov.cms.madie.user.config.security.RoleConstants;
import gov.cms.madie.user.config.security.SecurityExceptionHandlers;
import gov.cms.madie.user.config.security.UserRoleConverter;
import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UserExportService;
import gov.cms.madie.user.services.UserService;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class})
@ActiveProfiles("test")
@Import({
  SecurityConfig.class,
  RoleConstants.class,
  SecurityExceptionHandlers.class,
  UserRoleConverter.class
})
public class AdminControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private UpdateUserJobScheduler updateUserJobScheduler;
  @MockitoBean private UserExportService userExportService;
  @MockitoBean private JwtDecoder jwtDecoder;
  private static final String ADMIN_TEST_API_KEY = "0a51991c";
  private static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"MADIE-ADMIN"})
  void refreshAllUsersSuccessfullyTriggersJob() throws Exception {
    UserUpdatesJobResultDto results =
        UserUpdatesJobResultDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("user1", "user2", "user3")))
            .failedHarpIds(new ArrayList<>(List.of("user4", "user5")))
            .build();

    doNothing().when(updateUserJobScheduler).triggerUpdateUsersJobManually(null);

    mockMvc
        .perform(
            put("/admin/users/refresh")
                .with(csrf())
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());

    verify(updateUserJobScheduler, times(1)).triggerUpdateUsersJobManually(null);
  }

  @Test
  void refreshAllUsersRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            put("/admin/users/refresh")
                .with(csrf())
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(
            jsonPath("$.message").value("Authentication is required to access this resource"))
        .andExpect(jsonPath("$.path").value("/admin/users/refresh"));

    verify(updateUserJobScheduler, never()).triggerUpdateUsersJobManually(null);
  }

  @Test
  @WithMockUser(
      username = "regularUser",
      roles = {"MADIE-USER"})
  void refreshAllUsersReturnsForbiddenForNonAdminUser() throws Exception {
    mockMvc
        .perform(
            put("/admin/users/refresh")
                .with(csrf())
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(
            jsonPath("$.message")
                .value("User does not have the required role to access this resource"))
        .andExpect(jsonPath("$.path").value("/admin/users/refresh"));

    verify(updateUserJobScheduler, never()).triggerUpdateUsersJobManually(null);
  }

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"MADIE-ADMIN"})
  void refreshAllUsersRequiresCsrfToken() throws Exception {
    mockMvc
        .perform(
            put("/admin/users/refresh")
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    verify(updateUserJobScheduler, never()).triggerUpdateUsersJobManually(null);
  }

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"MADIE-ADMIN"})
  void getLastLoginReturnsUserList() throws Exception {
    List<UserLoginDto> users =
        List.of(
            UserLoginDto.builder()
                .harpId("user1")
                .lastLoginAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build(),
            UserLoginDto.builder()
                .harpId("user2")
                .lastLoginAt(Instant.parse("2026-02-20T14:30:00Z"))
                .build());
    when(userService.getAllMadieUsers()).thenReturn(users);

    mockMvc
        .perform(
            get("/admin/users/last-login")
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].harpId").value("user1"))
        .andExpect(jsonPath("$[1].harpId").value("user2"));

    verify(userService, times(1)).getAllMadieUsers();
  }

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"MADIE-ADMIN"})
  void getLastLoginReturnsEmptyListWhenNoUsers() throws Exception {
    when(userService.getAllMadieUsers()).thenReturn(Collections.emptyList());

    mockMvc
        .perform(
            get("/admin/users/last-login")
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());

    verify(userService, times(1)).getAllMadieUsers();
  }

  @Test
  void getLastLoginRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            get("/admin/users/last-login")
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(
            jsonPath("$.message").value("Authentication is required to access this resource"))
        .andExpect(jsonPath("$.path").value("/admin/users/last-login"));

    verify(userService, never()).getAllMadieUsers();
  }

  @Test
  @WithMockUser(
      username = "regularUser",
      roles = {"MADIE-USER"})
  void getLastLoginReturnsForbiddenForNonAdminUser() throws Exception {
    mockMvc
        .perform(
            get("/admin/users/last-login")
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(
            jsonPath("$.message")
                .value("User does not have the required role to access this resource"))
        .andExpect(jsonPath("$.path").value("/admin/users/last-login"));

    verify(userService, never()).getAllMadieUsers();
  }

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"MADIE-ADMIN"})
  void exportUsersReturnsWorkbookForAdmin() throws Exception {
    byte[] workbook = "fake-xlsx-bytes".getBytes(StandardCharsets.UTF_8);
    when(userExportService.generateUserExport(any())).thenReturn(workbook);

    mockMvc
        .perform(get("/admin/users/export").accept(XLSX_MEDIA_TYPE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(XLSX_MEDIA_TYPE))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    matchesPattern("attachment; filename=\"UserExport_\\d{8}_\\d{6}\\.xlsx\"")))
        .andExpect(content().bytes(workbook));

    verify(userExportService, times(1)).generateUserExport(any());
  }

  @Test
  @WithMockUser(
      username = "regularUser",
      roles = {"MADIE-USER"})
  void exportUsersReturnsForbiddenForNonAdminUser() throws Exception {
    mockMvc
        .perform(get("/admin/users/export").accept(XLSX_MEDIA_TYPE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(jsonPath("$.path").value("/admin/users/export"));

    verify(userExportService, never()).generateUserExport(any());
  }

  @Test
  void exportUsersRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/admin/users/export").accept(XLSX_MEDIA_TYPE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.path").value("/admin/users/export"));

    verify(userExportService, never()).generateUserExport(any());
  }
}
