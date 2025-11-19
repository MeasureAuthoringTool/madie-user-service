package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.SecurityConfig;
import gov.cms.madie.user.dto.SyncJobResultsDto;
import gov.cms.madie.user.services.UserService;
import gov.cms.madie.user.services.UserSyncScheduler;
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

@WebMvcTest({UserController.class})
@ActiveProfiles("test")
@Import(SecurityConfig.class)
public class UserControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private UserSyncScheduler userSyncScheduler;

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersSuccessfullyTriggersSync() throws Exception {
    // Given
    List<String> updatedIds = new ArrayList<>(List.of("user1", "user2", "user3"));
    List<String> failedIds = new ArrayList<>(List.of("user4"));
    List<String> unchangedIds = new ArrayList<>(List.of("user5", "user6"));

    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(updatedIds)
            .failedHarpIds(failedIds)
            .unchangedHarpIds(unchangedIds)
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(3)))
        .andExpect(jsonPath("$.updatedHarpIds[0]", is("user1")))
        .andExpect(jsonPath("$.updatedHarpIds[1]", is("user2")))
        .andExpect(jsonPath("$.updatedHarpIds[2]", is("user3")))
        .andExpect(jsonPath("$.failedHarpIds", hasSize(1)))
        .andExpect(jsonPath("$.failedHarpIds[0]", is("user4")))
        .andExpect(jsonPath("$.unchangedHarpIds", hasSize(2)))
        .andExpect(jsonPath("$.unchangedHarpIds[0]", is("user5")))
        .andExpect(jsonPath("$.unchangedHarpIds[1]", is("user6")));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersReturnsEmptyResultsWhenNoUsers() throws Exception {
    // Given
    SyncJobResultsDto emptyResults =
        SyncJobResultsDto.builder()
            .updatedHarpIds(new ArrayList<>())
            .failedHarpIds(new ArrayList<>())
            .unchangedHarpIds(new ArrayList<>())
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(emptyResults);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(0)))
        .andExpect(jsonPath("$.failedHarpIds", hasSize(0)))
        .andExpect(jsonPath("$.unchangedHarpIds", hasSize(0)));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersHandlesAllUpdatedScenario() throws Exception {
    // Given - all users successfully updated
    List<String> updatedIds = new ArrayList<>(List.of("user1", "user2", "user3", "user4", "user5"));

    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(updatedIds)
            .failedHarpIds(new ArrayList<>())
            .unchangedHarpIds(new ArrayList<>())
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(5)))
        .andExpect(jsonPath("$.failedHarpIds", empty()))
        .andExpect(jsonPath("$.unchangedHarpIds", empty()));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersHandlesAllFailedScenario() throws Exception {
    // Given - all users failed
    List<String> failedIds = new ArrayList<>(List.of("user1", "user2", "user3"));

    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(new ArrayList<>())
            .failedHarpIds(failedIds)
            .unchangedHarpIds(new ArrayList<>())
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", empty()))
        .andExpect(jsonPath("$.failedHarpIds", hasSize(3)))
        .andExpect(jsonPath("$.unchangedHarpIds", empty()));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersHandlesAllUnchangedScenario() throws Exception {
    // Given - all users unchanged
    List<String> unchangedIds = new ArrayList<>(List.of("user1", "user2"));

    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(new ArrayList<>())
            .failedHarpIds(new ArrayList<>())
            .unchangedHarpIds(unchangedIds)
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", empty()))
        .andExpect(jsonPath("$.failedHarpIds", empty()))
        .andExpect(jsonPath("$.unchangedHarpIds", hasSize(2)));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersHandlesMixedResults() throws Exception {
    // Given - mixed results with some of each
    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("updated1", "updated2")))
            .failedHarpIds(new ArrayList<>(List.of("failed1")))
            .unchangedHarpIds(new ArrayList<>(List.of("unchanged1", "unchanged2", "unchanged3")))
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(2)))
        .andExpect(jsonPath("$.failedHarpIds", hasSize(1)))
        .andExpect(jsonPath("$.unchangedHarpIds", hasSize(3)));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }

  @Test
  void refreshAllUsersRequiresAuthentication() throws Exception {
    // When & Then - no authentication
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    verify(userSyncScheduler, never()).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "testuser")
  void refreshAllUsersRequiresCsrfToken() throws Exception {
    // When & Then - no CSRF token
    mockMvc
        .perform(put("/users/all-users-refresh").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    verify(userSyncScheduler, never()).triggerManualSync();
  }

  @Test
  @WithMockUser(username = "adminuser")
  void refreshAllUsersWorksForDifferentUsers() throws Exception {
    // Given
    SyncJobResultsDto results =
        SyncJobResultsDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("user1")))
            .failedHarpIds(new ArrayList<>())
            .unchangedHarpIds(new ArrayList<>())
            .build();

    when(userSyncScheduler.triggerManualSync()).thenReturn(results);

    // When & Then
    mockMvc
        .perform(
            put("/users/all-users-refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedHarpIds", hasSize(1)))
        .andExpect(jsonPath("$.updatedHarpIds[0]", is("user1")));

    verify(userSyncScheduler, times(1)).triggerManualSync();
  }
}
