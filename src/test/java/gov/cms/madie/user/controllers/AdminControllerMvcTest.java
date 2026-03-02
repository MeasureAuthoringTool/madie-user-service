package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.SecurityConfig;
import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UserService;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class})
@ActiveProfiles("test")
@Import(SecurityConfig.class)
public class AdminControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private UpdateUserJobScheduler updateUserJobScheduler;
  private static final String ADMIN_TEST_API_KEY = "0a51991c";

  @Test
  @WithMockUser(username = "admin")
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
        .andExpect(status().isUnauthorized());

    verify(updateUserJobScheduler, never()).triggerUpdateUsersJobManually(null);
  }

  @Test
  @WithMockUser(username = "admin")
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
  @WithMockUser(username = "admin")
  void getLastLoginReturnsUserList() throws Exception {
    List<UserLoginDto> users =
        List.of(
            UserLoginDto.builder().harpId("user1").lastLoginAt(Instant.parse("2026-01-15T10:00:00Z")).build(),
            UserLoginDto.builder().harpId("user2").lastLoginAt(Instant.parse("2026-02-20T14:30:00Z")).build());
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
  @WithMockUser(username = "admin")
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
        .andExpect(status().isUnauthorized());

    verify(userService, never()).getAllMadieUsers();
  }
}
