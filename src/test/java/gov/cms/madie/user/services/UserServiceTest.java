package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.HarpResponseWrapper;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserRole;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.repositories.UserRepository;
import gov.cms.madie.user.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock TokenManager tokenManager;
  @Mock HarpProxyService harpProxyService;
  @Mock UserRepository userRepository;
  @Mock HarpConfig harpConfig;
  @InjectMocks private UserService userService;

  private TokenResponse tokenResponse;

  @BeforeEach
  void setUp() {
    tokenResponse = TokenResponse.builder().accessToken("test-token").build();
  }

  @Test
  void getUserByHarpIdReturnsExistingUser() {
    MadieUser existing = createExistingUser();
    when(userRepository.findByHarpId(existing.getHarpId())).thenReturn(Optional.of(existing));

    MadieUser actual = userService.getUserByHarpId(existing.getHarpId());

    assertThat(actual, is(existing));
    assertThat(actual.getEmail(), is(existing.getEmail()));
    verify(userRepository).findByHarpId(existing.getHarpId());
  }

  @Test
  void getUserByHarpIdReturnsNewUserWhenNotFound() {
    String harpId = "missing123";
    when(userRepository.findByHarpId(harpId)).thenReturn(Optional.empty());

    MadieUser actual = userService.getUserByHarpId(harpId);

    assertThat(actual.getHarpId(), is(harpId));
    assertNull(actual.getEmail());
    verify(userRepository).findByHarpId(harpId);
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUser() {
    // given
    String harpId = "bbb222";
    when(tokenManager.getCurrentToken())
        .thenReturn(TokenResponse.builder().accessToken("fake.jwt").build());
    when(harpProxyService.fetchUserRoles(harpId, "fake.jwt"))
        .thenReturn(
            HarpResponseWrapper.<UserRolesResponse>builder()
                    .response(UserRolesResponse.builder()
                            .userRoles(
                                    List.of(
                                            UserRole.builder()
                                                    .roleType("Group")
                                                    .programName("MADiE")
                                                    .displayName("MADiE-User")
                                                    .build()))
                            .build())
                    .statusCode(HttpStatus.OK)
                    .build());
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    when(userRepository.loginUser(ArgumentMatchers.any(MadieUser.class)))
        .thenReturn(MadieUser.builder().harpId("bbb222").build());
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUserWhenTokenIsNull() {
    // given
    String harpId = "nullTokenUser";
    when(tokenManager.getCurrentToken()).thenReturn(null);
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUserWhenAccessTokenIsBlank() {
    // given
    String harpId = "blankTokenUser";
    when(tokenManager.getCurrentToken())
        .thenReturn(TokenResponse.builder().accessToken("").build());
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void getUserDetailsByHarpIdReturnsDetails() {
    String harpId = "detail123";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .email("user@example.com")
            .firstName("Jane")
            .lastName("Smith")
            .build();
    when(userRepository.findByHarpId(harpId)).thenReturn(Optional.of(user));

    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);

    assertThat(details.getHarpId(), is(harpId));
    assertThat(details.getEmail(), is("user@example.com"));
    assertThat(details.getFirstName(), is("Jane"));
    assertThat(details.getLastName(), is("Smith"));
  }

  @Test
  void getUserDetailsByHarpIdReturnsEmptyDetailsWhenNotFound() {
    String harpId = "notfound";
    when(userRepository.findByHarpId(harpId)).thenReturn(Optional.empty());

    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);

    assertThat(details.getHarpId(), is(harpId));
    assertNull(details.getEmail());
  }

  @Test
  void updateUsersFromHarpReturnsEmptyWhenNoIdsProvided() {
    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(Collections.emptyList());

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verifyNoInteractions(tokenManager);
    verifyNoInteractions(userRepository);
  }

  @Test
  void updateUsersFromHarpReturnsEmptyWhenNullIdsProvided() {
    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(null);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verifyNoInteractions(tokenManager);
  }

  @Test
  void updateUsersFromHarpHandlesTokenRetrievalFailure() {
    List<String> harpIds = List.of("user1", "user2");
    when(tokenManager.getCurrentToken()).thenThrow(new RuntimeException("Token error"));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verify(tokenManager).getCurrentToken();
    verifyNoInteractions(userRepository);
  }

  @Test
  void updateUsersFromHarpHandlesUserDetailsFetchFailure() {
    List<String> harpIds = List.of("user1", "user2");
    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString()))
        .thenThrow(new RuntimeException("Fetch error"));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), containsInAnyOrder("user1", "user2"));
    verify(userRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpSuccessfully() {
    List<String> harpIds = List.of("harper");
    UserDetailsResponse detailsResponse =
        createUserDetailsResponse("harper", "harper@example.com", "Harper", "Lees");
    UserRolesResponse rolesResponse = createUserRolesResponse("active", "Admin", "ADMIN");
    MadieUser existingUser = createExistingUser();

    setupMocksForSuccessfulUpdate(harpIds, detailsResponse, rolesResponse, existingUser);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("harper"));
    assertThat(results.getFailedHarpIds(), empty());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(userRepository).updateMadieUser(updatesCaptor.capture(), eq("harper"));

    Map<String, Object> updates = updatesCaptor.getValue();
    assertThat(updates.get("email"), is("harper@example.com"));
    assertThat(updates.get("firstName"), is("Harper"));
    assertThat(updates.get("lastName"), is("Lees"));
    assertThat(updates.get("status"), is(UserStatus.ACTIVE));

    @SuppressWarnings("unchecked")
    List<HarpRole> roles = (List<HarpRole>) updates.get("roles");
    assertThat(roles.get(0).getRole(), is("Admin"));
  }

  @Test
  void updateUsersFromHarpHandlesNoUserDetailsReturned() {
    List<String> harpIds = List.of("user1");
    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(null);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verify(userRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpHandlesEmptyUserDetailsResponse() {
    List<String> harpIds = List.of("user1");
    UserDetailsResponse emptyResponse = new UserDetailsResponse();
    emptyResponse.setUserdetails(Collections.emptyList());

    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(emptyResponse);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    verify(userRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpMarksFailedWhenIndividualUpdateThrows() {
    List<String> harpIds = List.of("harper");

    UserDetail detail =
        UserDetail.builder()
            .username("harper")
            .email("test@example.com")
            .firstname("Test")
            .lastname("User")
            .displayname("Test User")
            .createdate("2025-10-29 13:48:37")
            .updatedate("2025-10-29 13:48:37")
            .build();
    UserDetailsResponse detailsResponse = new UserDetailsResponse();
    detailsResponse.setUserdetails(List.of(detail));

    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("harper"), anyString()))
        .thenThrow(new RuntimeException("Role fetch failed"));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), hasItem("harper"));
  }

  @Test
  void updateUsersFromHarpDeactivatesUserWithNoActiveRoles() {
    List<String> harpIds = List.of("inactive");

    UserDetail detail =
        UserDetail.builder()
            .username("inactive")
            .email("inactive@example.com")
            .firstname("Inactive")
            .lastname("User")
            .displayname("Inactive User")
            .createdate("2025-10-29 13:48:37")
            .updatedate("2025-10-29 13:48:37")
            .build();
    UserDetailsResponse detailsResponse = new UserDetailsResponse();
    detailsResponse.setUserdetails(List.of(detail));

    UserRolesResponse rolesResponse = new UserRolesResponse();
    rolesResponse.setUserRoles(Collections.emptyList());

    MadieUser existingUser =
        MadieUser.builder()
            .harpId("inactive")
            .status(UserStatus.ACTIVE)
            .roles(List.of(HarpRole.builder().role("OldRole").build()))
            .build();

    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("inactive"), anyString())).thenReturn(
        HarpResponseWrapper.<UserRolesResponse>builder()
            .response(rolesResponse)
            .statusCode(HttpStatus.OK)
            .build());
    when(userRepository.findByHarpId("inactive")).thenReturn(Optional.of(existingUser));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("inactive"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(userRepository).updateMadieUser(updatesCaptor.capture(), eq("inactive"));

    Map<String, Object> updates = updatesCaptor.getValue();
    assertThat(updates.get("status"), is(UserStatus.DEACTIVATED));
    assertThat((List<?>) updates.get("roles"), empty());
  }

  @Test
  void updateUsersFromHarpAddsNewRolesToExistingUser() {
    List<String> harpIds = List.of("user");
    UserDetailsResponse detailsResponse =
        createUserDetailsResponse("user", "user@example.com", "Test", "User");
    UserRolesResponse rolesResponse =
        createMultiRoleResponse(List.of("Role1", "Role2"), List.of("TYPE1", "TYPE2"));
    MadieUser existingUser = createUserWithRole();

    setupMocksForSuccessfulUpdate(harpIds, detailsResponse, rolesResponse, existingUser);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("user"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(userRepository).updateMadieUser(updatesCaptor.capture(), eq("user"));

    Map<String, Object> updates = updatesCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<HarpRole> updatedRoles = (List<HarpRole>) updates.get("roles");
    assertThat(updatedRoles, hasSize(2));
  }

  @Test
  void getMostRecentStartDateReturnsNullForNullResponse() {
    assertThat(userService.getMostRecentStartDate(null), is(nullValue()));
  }

  @Test
  void getMostRecentStartDateReturnsNullForEmptyRoles() {
    UserRolesResponse response = UserRolesResponse.builder().userRoles(List.of()).build();
    assertThat(userService.getMostRecentStartDate(response), is(nullValue()));
  }

  @Test
  void getMostRecentStartDateReturnsNullForNullStartDates() {
    UserRolesResponse response =
        UserRolesResponse.builder()
            .userRoles(List.of(UserRole.builder().startDate(null).build()))
            .build();
    assertThat(userService.getMostRecentStartDate(response), is(nullValue()));
  }

  @Test
  void getMostRecentStartDateReturnsMostRecentInstant() {
    String date1 = "2023-01-01 10:00:00";
    String date2 = "2024-05-10 15:30:00";
    UserRolesResponse response =
        UserRolesResponse.builder()
            .userRoles(
                List.of(
                    UserRole.builder().startDate(date1).build(),
                    UserRole.builder().startDate(date2).build()))
            .build();
    Instant result = userService.getMostRecentStartDate(response);
    Instant expected =
        java.time.LocalDateTime.parse(
                date2, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant();
    assertThat(result, is(expected));
  }

  @Test
  void getMostRecentStartDateIgnoresInvalidDates() {
    String validDate = "2022-12-31 23:59:59";
    String invalidDate = "not-a-date";
    UserRolesResponse response =
        UserRolesResponse.builder()
            .userRoles(
                List.of(
                    UserRole.builder().startDate(invalidDate).build(),
                    UserRole.builder().startDate(validDate).build()))
            .build();
    Instant result = userService.getMostRecentStartDate(response);
    Instant expected =
        java.time.LocalDateTime.parse(
                validDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant();
    assertThat(result, is(expected));
  }

  @Test
  void getMostRecentStartDateReturnsNullForNullUserRolesList() {
    UserRolesResponse response = UserRolesResponse.builder().userRoles(null).build();
    assertThat(userService.getMostRecentStartDate(response), is(nullValue()));
  }

  @Test
  void getStatusForRolesReturnsActiveWhenMatchingRoleExists() {
    // given
    String programName = "MADiE";
    when(harpConfig.getProgramName()).thenReturn(programName);
    UserRolesResponse response = UserRolesResponse.builder()
        .userRoles(List.of(UserRole.builder().programName(programName).build()))
        .build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(response)
        .statusCode(HttpStatus.OK)
        .build();
    // when
    var status = userService.getStatusForRoles(wrapper);
    // then
    assertThat(status, is(UserStatus.ACTIVE));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenNoRoles() {
    // given
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    UserRolesResponse response = UserRolesResponse.builder().userRoles(List.of()).build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(response)
        .statusCode(HttpStatus.OK)
        .build();
    // when
    var status = userService.getStatusForRoles(wrapper);
    // then
    assertThat(status, is(UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenNoMatchingProgramName() {
    // given
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    UserRolesResponse response = UserRolesResponse.builder()
        .userRoles(List.of(UserRole.builder().programName("OTHER").build()))
        .build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(response)
        .statusCode(HttpStatus.OK)
        .build();
    // when
    var status = userService.getStatusForRoles(wrapper);
    // then
    assertThat(status, is(UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenResponseIsNull() {
    // given
    // when
    var status = userService.getStatusForRoles(null);
    // then
    assertThat(status, is(UserStatus.ERROR_SUSPENDED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenUserRolesIsNull() {
    // given
    UserRolesResponse response = UserRolesResponse.builder().userRoles(null).build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(response)
        .statusCode(HttpStatus.OK)
        .build();
    // when
    var status = userService.getStatusForRoles(wrapper);
    // then
    assertThat(status, is(UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedForRoleCreation027Error() {
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR)
        .error(gov.cms.madie.user.dto.HarpErrorResponse.builder().errorCode("ERR-ROLECREATION-027").build())
        .build();
    var status = userService.getStatusForRoles(wrapper);
    assertThat(status, is(UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsErrorSuspendedForOtherErrorCodes() {
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR)
        .error(gov.cms.madie.user.dto.HarpErrorResponse.builder().errorCode("ERR-OTHER").build())
        .build();
    var status = userService.getStatusForRoles(wrapper);
    assertThat(status, is(UserStatus.ERROR_SUSPENDED));
  }

  @Test
  void testAreHarpIdsValid() {
    // given
    List<String> harpIds = List.of("harp1", "harp2", "harp3");
    when(userRepository.countByHarpIdIn(anyList())).thenReturn(3);
    // when & then
    assertTrue(userService.areHarpIdsValid(harpIds));
  }

  @Test
  void testAreHarpIdsValidWhenInvalid() {
    // given
    when(userRepository.countByHarpIdIn(anyList())).thenReturn(2);
    // when & then
    assertFalse(userService.areHarpIdsValid(List.of("harp1", "harp2", "harp3")));
  }

  @Test
  void buildMadieUserReturnsActiveWithValidRoles() {
    UserDetail detail = UserDetail.builder()
        .username("activeuser")
        .email("active@example.com")
        .firstname("Active")
        .lastname("User")
        .displayname("Active User")
        .createdate("2025-10-29 13:48:37")
        .updatedate("2025-10-29 13:48:37")
        .build();
    UserRole validRole = UserRole.builder()
        .programName("MADiE")
        .roleType("MADiE")
        .displayName("MADiE-User")
        .status("Active")
        .build();
    UserRolesResponse rolesResponse = UserRolesResponse.builder()
        .userRoles(List.of(validRole))
        .build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(rolesResponse)
        .statusCode(HttpStatus.OK)
        .build();
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    MadieUser user = userService.buildMadieUser(detail, wrapper);
    assertThat(user.getStatus(), is(UserStatus.ACTIVE));
    assertThat(user.getRoles(), hasSize(1));
    assertThat(user.getRoles().get(0).getRole(), is("MADiE-User"));
  }

  @Test
  void buildMadieUserReturnsDeactivatedWithNoMatchingRoles() {
    UserDetail detail = UserDetail.builder().username("inactive").build();
    UserRole nonMatchingRole = UserRole.builder()
        .programName("OTHER")
        .roleType("OTHER")
        .displayName("Other-User")
        .status("Active")
        .build();
    UserRolesResponse rolesResponse = UserRolesResponse.builder()
        .userRoles(List.of(nonMatchingRole))
        .build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(rolesResponse)
        .statusCode(HttpStatus.OK)
        .build();
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    MadieUser user = userService.buildMadieUser(detail, wrapper);
    assertThat(user.getStatus(), is(UserStatus.DEACTIVATED));
    assertThat(user.getRoles(), empty());
  }

  @Test
  void buildMadieUserReturnsDeactivatedForRoleCreation027Error() {
    UserDetail detail = UserDetail.builder().username("deactivated").build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR)
        .error(gov.cms.madie.user.dto.HarpErrorResponse.builder().errorCode("ERR-ROLECREATION-027").build())
        .build();
    MadieUser user = userService.buildMadieUser(detail, wrapper);
    assertThat(user.getStatus(), is(UserStatus.DEACTIVATED));
    assertThat(user.getRoles(), empty());
  }

  @Test
  void buildMadieUserReturnsErrorSuspendedForOtherErrorCodes() {
    UserDetail detail = UserDetail.builder().username("suspended").build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR)
        .error(gov.cms.madie.user.dto.HarpErrorResponse.builder().errorCode("ERR-OTHER").build())
        .build();
    MadieUser user = userService.buildMadieUser(detail, wrapper);
    assertThat(user.getStatus(), is(UserStatus.ERROR_SUSPENDED));
    assertThat(user.getRoles(), empty());
  }

  @Test
  void buildMadieUserReturnsErrorSuspendedForNullWrapper() {
    UserDetail detail = UserDetail.builder().username("nullwrapper").build();
    MadieUser user = userService.buildMadieUser(detail, null);
    assertThat(user.getStatus(), is(UserStatus.ERROR_SUSPENDED));
    assertThat(user.getRoles(), empty());
  }

  @Test
  void buildMadieUserHandlesNullDetail() {
    UserRole validRole = UserRole.builder()
        .programName("MADiE")
        .roleType("MADiE")
        .displayName("MADiE-User")
        .status("Active")
        .build();
    UserRolesResponse rolesResponse = UserRolesResponse.builder()
        .userRoles(List.of(validRole))
        .build();
    HarpResponseWrapper<UserRolesResponse> wrapper = HarpResponseWrapper.<UserRolesResponse>builder()
        .response(rolesResponse)
        .statusCode(HttpStatus.OK)
        .build();
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    MadieUser user = userService.buildMadieUser(null, wrapper);
    assertThat(user.getStatus(), is(UserStatus.ACTIVE));
    assertThat(user.getRoles(), hasSize(1));
  }

  @Test
  void updateUsersFromHarpDoesNotUpdateWhenUpdatedUserIsNull() {
    List<String> harpIds = List.of("nulluser");
    UserDetailsResponse detailsResponse = createUserDetailsResponse("nulluser", "null@example.com", "Null", "User");
    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    // Spy UserService to force buildMadieUser to return null
    UserService spyService = spy(userService);
    doReturn(null).when(spyService).buildMadieUser(any(), any());
    UserUpdatesJobResultDto result = spyService.updateUsersFromHarp(harpIds);
    // Should not update user, should mark as failed
    assertThat(result.getUpdatedHarpIds(), empty());
    assertThat(result.getFailedHarpIds(), hasItem("nulluser"));
    verify(userRepository, never()).updateMadieUser(anyMap(), eq("nulluser"));
  }

  // Helper method for test setup
  private UserDetailsResponse createUserDetailsResponse(String username, String email, String firstName, String lastName) {
    UserDetail detail = UserDetail.builder()
        .username(username)
        .email(email)
        .firstname(firstName)
        .lastname(lastName)
        .displayname(firstName + " " + lastName)
        .createdate("2025-10-29 13:48:37")
        .updatedate("2025-11-15 10:30:00")
        .build();
    UserDetailsResponse response = new UserDetailsResponse();
    response.setUserdetails(List.of(detail));
    return response;
  }

  private UserRolesResponse createUserRolesResponse(
          String status, String displayName, String roleType) {
    UserRole userRole =
            UserRole.builder()
                    .programName("MADiE")
                    .status(status)
                    .displayName(displayName)
                    .roleType(roleType)
                    .build();
    UserRolesResponse response = new UserRolesResponse();
    response.setUserRoles(List.of(userRole));
    return response;
  }

  private UserRolesResponse createMultiRoleResponse(
          List<String> displayNames, List<String> roleTypes) {
    List<UserRole> roles = new ArrayList<>();
    for (int i = 0; i < displayNames.size(); i++) {
      roles.add(
              UserRole.builder()
                      .programName("MADiE")
                      .status("active")
                      .displayName(displayNames.get(i))
                      .roleType(roleTypes.get(i))
                      .build());
    }
    UserRolesResponse response = new UserRolesResponse();
    response.setUserRoles(roles);
    return response;
  }

  private MadieUser createExistingUser() {
    return MadieUser.builder()
            .harpId("harper")
            .email("old@example.com")
            .firstName("Old")
            .lastName("Name")
            .roles(new ArrayList<>())
            .status(UserStatus.DEACTIVATED)
            .build();
  }

  private MadieUser createUserWithRole() {
    HarpRole existingRole = HarpRole.builder().role("Role1").roleType("TYPE1").build();
    return MadieUser.builder()
            .harpId("user")
            .email("user@example.com")
            .firstName("Test")
            .lastName("User")
            .displayName("Test" + " " + "User")
            .status(UserStatus.ACTIVE)
            .roles(new ArrayList<>(List.of(existingRole)))
            .build();
  }

  private void setupMocksForSuccessfulUpdate(
          List<String> harpIds,
          UserDetailsResponse detailsResponse,
          UserRolesResponse rolesResponse,
          MadieUser existingUser) {
    HarpResponseWrapper<UserRolesResponse> rolesWrapper = HarpResponseWrapper.<UserRolesResponse>builder()
            .response(rolesResponse)
            .statusCode(HttpStatus.OK)
            .build();
    when(tokenManager.getCurrentToken()).thenReturn(tokenResponse);
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(anyString(), anyString())).thenReturn(rolesWrapper);
    when(userRepository.findByHarpId(anyString())).thenReturn(Optional.of(existingUser));
  }

}
