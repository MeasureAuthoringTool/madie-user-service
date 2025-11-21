package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.SecurityConfig;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    when(updateUserJobScheduler.triggerUpdateUsersJobManually(null)).thenReturn(results);

    mockMvc
        .perform(
            put("/admin/users/refresh")
                .with(csrf())
                .header("api-key", ADMIN_TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(3)))
        .andExpect(jsonPath("$.updatedHarpIds[0]", is("user1")))
        .andExpect(jsonPath("$.updatedHarpIds[1]", is("user2")))
        .andExpect(jsonPath("$.updatedHarpIds[2]", is("user3")))
        .andExpect(jsonPath("$.failedHarpIds", hasSize(2)))
        .andExpect(jsonPath("$.failedHarpIds[0]", is("user4")))
        .andExpect(jsonPath("$.failedHarpIds[1]", is("user5")));

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
}
