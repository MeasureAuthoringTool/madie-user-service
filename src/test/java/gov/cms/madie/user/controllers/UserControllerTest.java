package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UserService;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
  @Mock private UserService userService;
  @Mock private Principal principal;
  @Mock private UpdateUserJobScheduler updateUserJobScheduler;
  @InjectMocks private UserController userController;

  @BeforeEach
  void setUp() {
    when(principal.getName()).thenReturn("testUser");
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", null);
  }

  @Test
  void getUserReturnsMadieUser() {
    // given
    MadieUser user = MadieUser.builder().harpId("123").build();
    when(userService.getUserByHarpId("123")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.getUser("123", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
  }

  @Test
  void updateUserReturnsUpdatedMadieUser() {
    // given
    MadieUser user = MadieUser.builder().harpId("123").build();
    when(principal.getName()).thenReturn("123"); // principal matches harpId
    when(userService.refreshUserRolesAndLogin("123")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.updateUser("123", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
  }

  @Test
  void updateUserUsesOverrideTestIdWhenSet() {
    // given
    MadieUser user = MadieUser.builder().harpId("overrideId").build();
    // Simulate the test override ID being set
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", "overrideId");
    when(userService.refreshUserRolesAndLogin("overrideId")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.updateUser("anyId", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
  }

  @Test
  void updateUserUsesPathVariableWhenOverrideTestIdIsEmptyString() {
    // given
    MadieUser user = MadieUser.builder().harpId("123").build();
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", "");
    when(principal.getName()).thenReturn("123");
    when(userService.refreshUserRolesAndLogin("123")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.updateUser("123", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
    verify(userService, times(1)).refreshUserRolesAndLogin("123");
  }

  @Test
  void updateUserThrowsForbiddenWhenPrincipalDoesNotMatchAndOverrideTestIdIsBlank() {
    // given
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", null);
    when(principal.getName()).thenReturn("principalId");
    // when & then
    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> userController.updateUser("differentId", principal)
    );
    assertThat(exception.getStatusCode(), is(HttpStatus.FORBIDDEN));
    assertThat(
      exception.getReason(),
      containsString("User [principalId] attempted to update user [differentId] - not allowed")
    );
  }

  @Test
  void updateUserUsesOverrideTestIdWhenPrincipalMatchesAndOverrideTestIdIsSet() {
    // given
    MadieUser user = MadieUser.builder().harpId("overrideId").build();
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", "overrideId");
    when(principal.getName()).thenReturn("overrideId");
    when(userService.refreshUserRolesAndLogin("overrideId")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.updateUser("overrideId", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
    verify(userService, times(1)).refreshUserRolesAndLogin("overrideId");
  }

  @Test
  void updateUserThrowsForbiddenWhenPrincipalDoesNotMatchAndOverrideTestIdIsEmptyString() {
    // given
    ReflectionTestUtils.setField(userController, "harpOverrideTestId", "");
    when(principal.getName()).thenReturn("principalId");
    // when & then
    ResponseStatusException exception = assertThrows(
      ResponseStatusException.class,
      () -> userController.updateUser("differentId", principal)
    );
    assertThat(exception.getStatusCode(), is(HttpStatus.FORBIDDEN));
    assertThat(
      exception.getReason(),
      containsString("User [principalId] attempted to update user [differentId] - not allowed")
    );
  }

  @Test
  void getUserActivityReportReturnsReport() {
    // when
    ResponseEntity<Object> response = userController.getUserActivityReport(principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is("User report coming soon"));
  }

  @Test
  void refreshAllUsersReturnsAccepted() {
    // given
    UserUpdatesJobResultDto resultsDto =
        UserUpdatesJobResultDto.builder()
            .failedHarpIds(List.of("John"))
            .updatedHarpIds(List.of("Bob"))
            .build();
    when(updateUserJobScheduler.triggerUpdateUserJobManually()).thenReturn(resultsDto);
    // when
    ResponseEntity<UserUpdatesJobResultDto> response = userController.refreshAllUsers(principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getFailedHarpIds(), is(resultsDto.getFailedHarpIds()));
    assertThat(response.getBody().getUpdatedHarpIds(), is(resultsDto.getUpdatedHarpIds()));
  }

  @Test
  void getUserDetailsReturnsUserDetailsDto() {
    // given
    UserDetailsDto details = UserDetailsDto.builder().harpId("123").build();
    when(userService.getUserDetailsByHarpId("123")).thenReturn(details);
    // when
    ResponseEntity<UserDetailsDto> response = userController.getUserDetails("123", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(details));
  }

  @Test
  void getBulkUserDetailsReturnsMapOfUserDetailsDto() {
    // given
    DetailsRequestDto request = new DetailsRequestDto();
    request.setHarpIds(Arrays.asList("123", "456"));
    UserDetailsDto details1 = UserDetailsDto.builder().harpId("123").build();
    UserDetailsDto details2 = UserDetailsDto.builder().harpId("456").build();
    when(userService.getUserDetailsByHarpId("123")).thenReturn(details1);
    when(userService.getUserDetailsByHarpId("456")).thenReturn(details2);
    // when
    ResponseEntity<Map<String, UserDetailsDto>> response =
        userController.getBulkUserDetails(request, principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), allOf(hasEntry("123", details1), hasEntry("456", details2)));
  }
}
