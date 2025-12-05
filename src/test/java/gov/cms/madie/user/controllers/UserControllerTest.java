package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.exceptions.InvalidHarpIdException;
import gov.cms.madie.user.services.UserService;
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
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
  @Mock private UserService userService;
  @Mock private Principal principal;
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
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userController.updateUser("differentId", principal));
    assertThat(exception.getStatusCode(), is(HttpStatus.FORBIDDEN));
    assertThat(
        exception.getReason(),
        containsString("User [principalId] attempted to update user [differentId] - not allowed"));
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
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userController.updateUser("differentId", principal));
    assertThat(exception.getStatusCode(), is(HttpStatus.FORBIDDEN));
    assertThat(
        exception.getReason(),
        containsString("User [principalId] attempted to update user [differentId] - not allowed"));
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

  @Test
  void getUserDetailsReturnsUserDetailsWhenHarpIdIsValid() {
    String harpId = "validHarpId";
    UserDetailsDto userDetails = UserDetailsDto.builder().harpId(harpId).build();
    when(userService.getUserDetailsByHarpId(harpId)).thenReturn(userDetails);

    ResponseEntity<UserDetailsDto> response = userController.getUserDetails(harpId, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(userDetails));
  }

  @Test
  void getUserDetailsThrowsExceptionWhenHarpIdIsNull() {
    InvalidHarpIdException exception =
        assertThrows(
            InvalidHarpIdException.class, () -> userController.getUserDetails(null, principal));

    assertThat(exception.getMessage(), is("HARP ID cannot be null or empty"));
  }

  @Test
  void getUserDetailsThrowsExceptionWhenHarpIdIsBlank() {
    InvalidHarpIdException exception =
        assertThrows(
            InvalidHarpIdException.class, () -> userController.getUserDetails("   ", principal));

    assertThat(exception.getMessage(), is("HARP ID cannot be null or empty"));
  }

  @Test
  void getUserDetailsThrowsExceptionWhenUserDetailsNotFound() {
    String harpId = "nonExistentHarpId";
    when(userService.getUserDetailsByHarpId(harpId)).thenReturn(null);

    InvalidHarpIdException exception =
        assertThrows(
            InvalidHarpIdException.class, () -> userController.getUserDetails(harpId, principal));

    assertThat(exception.getMessage(), is("User not found for HARP ID: [nonExistentHarpId]"));
  }

  @Test
  void getBulkUserDetailsReturnsValidEntriesWhenAllHarpIdsAreValid() {
    DetailsRequestDto request = new DetailsRequestDto();
    request.setHarpIds(Arrays.asList("123", "456"));
    UserDetailsDto details1 = UserDetailsDto.builder().harpId("123").build();
    UserDetailsDto details2 = UserDetailsDto.builder().harpId("456").build();
    when(userService.getUserDetailsByHarpId("123")).thenReturn(details1);
    when(userService.getUserDetailsByHarpId("456")).thenReturn(details2);

    ResponseEntity<Map<String, UserDetailsDto>> response =
        userController.getBulkUserDetails(request, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), allOf(hasEntry("123", details1), hasEntry("456", details2)));
  }

  @Test
  void getBulkUserDetailsReturnsEntriesWithDefaultUserDetailsForInvalidHarpIds() {
    DetailsRequestDto request = new DetailsRequestDto();
    request.setHarpIds(Arrays.asList("123", "invalid"));
    UserDetailsDto details1 = UserDetailsDto.builder().harpId("123").build();
    when(userService.getUserDetailsByHarpId("123")).thenReturn(details1);
    when(userService.getUserDetailsByHarpId("invalid")).thenReturn(null);

    ResponseEntity<Map<String, UserDetailsDto>> response =
        userController.getBulkUserDetails(request, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(
        response.getBody(),
        allOf(
            hasEntry("123", details1),
            hasEntry("invalid", UserDetailsDto.builder().harpId("invalid").build())));
  }

  @Test
  void getBulkUserDetailsThrowsExceptionWhenHarpIdsAreEmpty() {
    DetailsRequestDto request = new DetailsRequestDto();
    request.setHarpIds(Arrays.asList());

    InvalidHarpIdException exception =
        assertThrows(
            InvalidHarpIdException.class,
            () -> userController.getBulkUserDetails(request, principal));

    assertThat(exception.getMessage(), is("Harp Ids cannot be null or empty"));
  }

  @Test
  void getBulkUserDetailsFiltersOutNullHarpIds() {
    DetailsRequestDto request = new DetailsRequestDto();
    request.setHarpIds(Arrays.asList("123", null, "456"));
    UserDetailsDto details1 = UserDetailsDto.builder().harpId("123").build();
    UserDetailsDto details2 = UserDetailsDto.builder().harpId("456").build();
    when(userService.getUserDetailsByHarpId("123")).thenReturn(details1);
    when(userService.getUserDetailsByHarpId("456")).thenReturn(details2);

    ResponseEntity<Map<String, UserDetailsDto>> response =
        userController.getBulkUserDetails(request, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), allOf(hasEntry("123", details1), hasEntry("456", details2)));
    assertThat(response.getBody().containsKey(null), is(false));
  }
}
