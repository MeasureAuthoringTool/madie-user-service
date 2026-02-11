package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.SecurityConfig;
import gov.cms.madie.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserController.class})
@ActiveProfiles("test")
@Import(SecurityConfig.class)
public class UserControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @Test
  @WithMockUser(username = "testuser")
  void getUserByHarpIdSuccessfully() throws Exception {
    // Given
    String harpId = "harper";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .firstName("John")
            .lastName("Doe")
            .roles(List.of(HarpRole.builder().role("MADiE-Admin").roleType("Group").build()))
            .build();
    when(userService.getUserByHarpId(harpId)).thenReturn(user);

    // When & Then
    mockMvc
        .perform(get("/users/" + harpId).with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.firstName", is("John")))
        .andExpect(jsonPath("$.lastName", is("Doe")))
        .andExpect(jsonPath("$.roles[0].roleType", is("Group")))
        .andExpect(jsonPath("$.roles[0].role", is("MADiE-Admin")));

    verify(userService, times(1)).getUserByHarpId(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void loginUserSuccessfully() throws Exception {
    // Given
    String harpId = "testuser";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .status(UserStatus.ACTIVE)
            .roles(
                List.of(
                    HarpRole.builder().role("MADiE-User").roleType("Group").build(),
                    HarpRole.builder().role("MADiE-Admin").roleType("Group").build()))
            .build();
    when(userService.refreshUserRolesAndLogin(harpId)).thenReturn(user);

    // When & Then
    mockMvc
        .perform(put("/users/" + harpId).with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.roles", hasSize(2)))
        .andExpect(jsonPath("$.roles[0].role", is("MADiE-User")))
        .andExpect(jsonPath("$.roles[0].roleType", is("Group")))
        .andExpect(jsonPath("$.roles[1].role", is("MADiE-Admin")))
        .andExpect(jsonPath("$.roles[1].roleType", is("Group")));

    verify(userService, times(1)).refreshUserRolesAndLogin(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void loginUserReturnsForbiddenWhenUserMismatch() throws Exception {
    // Given
    String harpId = "differentuser";

    // When & Then
    mockMvc
        .perform(put("/users/" + harpId).with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    verify(userService, never()).refreshUserRolesAndLogin(anyString());
  }

  @Test
  @WithMockUser(username = "testuser")
  void getUserDetailsSuccessfully() throws Exception {
    // Given
    String harpId = "testuser";
    UserDetailsDto userDetails =
        UserDetailsDto.builder().harpId(harpId).firstName("John").lastName("Doe").build();
    when(userService.getUserDetailsByHarpId(harpId)).thenReturn(userDetails);

    // When & Then
    mockMvc
        .perform(
            get("/users/" + harpId + "/details")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.firstName", is("John")))
        .andExpect(jsonPath("$.lastName", is("Doe")));

    verify(userService, times(1)).getUserDetailsByHarpId(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void getBulkUserDetailsSuccessfully() throws Exception {
    // Given
    UserDetailsDto user1 = UserDetailsDto.builder().harpId("user1").firstName("John").build();
    UserDetailsDto user2 = UserDetailsDto.builder().harpId("user2").firstName("Jane").build();
    when(userService.getUserDetailsByHarpId("user1")).thenReturn(user1);
    when(userService.getUserDetailsByHarpId("user2")).thenReturn(user2);

    String requestBody = "{\"harpIds\": [\"user1\", \"user2\"]}";

    // When & Then
    mockMvc
        .perform(
            post("/users/details")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.user1.harpId", is("user1")))
        .andExpect(jsonPath("$.user1.firstName", is("John")))
        .andExpect(jsonPath("$.user2.harpId", is("user2")))
        .andExpect(jsonPath("$.user2.firstName", is("Jane")));

    verify(userService, times(1)).getUserDetailsByHarpId("user1");
    verify(userService, times(1)).getUserDetailsByHarpId("user2");
  }

  @Test
  @WithMockUser(username = "testuser")
  void loginUserWithDeactivatedStatusSuccessfully() throws Exception {
    // Given
    String harpId = "testuser";
    MadieUser user =
        MadieUser.builder().harpId(harpId).status(UserStatus.DEACTIVATED).roles(List.of()).build();
    when(userService.refreshUserRolesAndLogin(harpId)).thenReturn(user);

    // When & Then
    mockMvc
        .perform(put("/users/" + harpId).with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.status", is("DEACTIVATED")))
        .andExpect(jsonPath("$.roles", hasSize(0)));

    verify(userService, times(1)).refreshUserRolesAndLogin(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void getUserDetailsReturnsNotFoundWhenHarpIdIsBlank() throws Exception {
    // When & Then
    mockMvc
        .perform(get("/users/ /details").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verify(userService, never()).getUserDetailsByHarpId(anyString());
  }

  @Test
  @WithMockUser(username = "testuser")
  void getBulkUserDetailsWithEmptyRequestBody() throws Exception {
    // Given
    String requestBody = "{\"harpIds\": []}";

    // When & Then
    mockMvc
        .perform(
            post("/users/details")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());

    verify(userService, never()).getUserDetailsByHarpId(anyString());
  }

  @Test
  @WithMockUser(username = "testuser")
  void getBulkUserDetailsWithNullHarpIds() throws Exception {
    // Given
    UserDetailsDto user1 = UserDetailsDto.builder().harpId("user1").firstName("John").build();
    when(userService.getUserDetailsByHarpId("user1")).thenReturn(user1);

    String requestBody = "{\"harpIds\": [\"user1\", null, \"user2\"]}";

    // When & Then
    mockMvc
        .perform(
            post("/users/details")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.user1.harpId", is("user1")))
        .andExpect(jsonPath("$.user1.firstName", is("John")));

    verify(userService, times(1)).getUserDetailsByHarpId("user1");
    verify(userService, never()).getUserDetailsByHarpId(isNull());
  }

  @Test
  @WithMockUser(username = "testuser")
  void getUserRolesByHarpIdSuccessfully() throws Exception {
    // Given
    String harpId = "harper";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .firstName("John")
            .lastName("Doe")
            .roles(List.of(HarpRole.builder().role("MADiE-Admin").roleType("Group").build()))
            .build();
    when(userService.getUserByHarpId(harpId)).thenReturn(user);

    // When & Then
    mockMvc
        .perform(
            get("/users/" + harpId + "/roles").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.roles[0]", is("MADiE-Admin")));

    verify(userService, times(1)).getUserByHarpId(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void getUserRolesMadieUserNotFound() throws Exception {
    String harpId = "nonexistent";
    when(userService.getUserByHarpId(harpId)).thenReturn(null);

    mockMvc
        .perform(
            get("/users/" + harpId + "/roles").with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Bad Request")))
        .andExpect(jsonPath("$.message", containsString("Harp Id: " + harpId + " is not found")));

    verify(userService, times(1)).getUserByHarpId(harpId);
  }

  @Test
  @WithMockUser(username = "testuser")
  void getUserRolesMadieUserDoesNotHaveRoles() throws Exception {

    String harpId = "userWithoutRoles";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .firstName("John")
            .lastName("Doe")
            .roles(List.of())
            .build();
    when(userService.getUserByHarpId(harpId)).thenReturn(user);

    try {
      mockMvc
          .perform(
              get("/users/" + harpId + "/roles")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error", is("Bad Request")))
          .andExpect(
              jsonPath(
                  "$.message",
                  containsString("Harp Id: " + harpId + " is not found or does not have roles")));
    } catch (Exception e) {
      e.printStackTrace();
    }

    verify(userService, times(1)).getUserByHarpId(harpId);
  }
}
