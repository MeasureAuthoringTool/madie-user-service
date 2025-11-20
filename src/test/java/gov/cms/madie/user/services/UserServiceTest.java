package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserRole;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.repositories.UserRepository;
import gov.cms.madie.user.dto.*;
import gov.cms.madie.user.repositories.CustomMadieUserRepository;
import gov.cms.madie.user.repositories.MadieUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  @Mock private MadieUserRepository madieUserRepository;
  @Mock private CustomMadieUserRepository customMadieUserRepository;
  @InjectMocks private UserService userService;

  private TokenResponse tokenResponse;

  @BeforeEach
  void setUp() {
    tokenResponse = TokenResponse.builder().accessToken("test-token").build();
  }

  @Test
  void getUserByHarpIdReturnsExistingUser() {
    String harpId = "abc123";
    MadieUser existing =
        MadieUser.builder()
            .harpId(harpId)
            .email("test@example.com")
            .firstName("John")
            .lastName("Doe")
            .build();
    when(madieUserRepository.findByHarpId(harpId)).thenReturn(Optional.of(existing));

    MadieUser actual = userService.getUserByHarpId(harpId);

    assertThat(actual, is(existing));
    assertThat(actual.getEmail(), is("test@example.com"));
    verify(madieUserRepository).findByHarpId(harpId);
  }

  @Test
  void getUserByHarpIdReturnsNewUserWhenNotFound() {
    String harpId = "missing123";
    when(madieUserRepository.findByHarpId(harpId)).thenReturn(Optional.empty());

    MadieUser actual = userService.getUserByHarpId(harpId);

    assertThat(actual.getHarpId(), is(harpId));
    assertNull(actual.getEmail());
    verify(madieUserRepository).findByHarpId(harpId);
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUser() {
    // given
    String harpId = "bbb222";
    when(tokenManager.getCurrentToken())
        .thenReturn(TokenResponse.builder().accessToken("fake.jwt").build());
    when(harpProxyService.fetchUserRoles(harpId, "fake.jwt"))
        .thenReturn(
            UserRolesResponse.builder()
                .userRoles(
                    List.of(
                        UserRole.builder()
                            .roleType("Group")
                            .programName("MADiE")
                            .displayName("MADiE-User")
                            .build()))
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
    when(madieUserRepository.findByHarpId(harpId)).thenReturn(Optional.of(user));

    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);

    assertThat(details.getHarpId(), is(harpId));
    assertThat(details.getEmail(), is("user@example.com"));
    assertThat(details.getFirstName(), is("Jane"));
    assertThat(details.getLastName(), is("Smith"));
  }

  @Test
  void getUserDetailsByHarpIdReturnsEmptyDetailsWhenNotFound() {
    String harpId = "notfound";
    when(madieUserRepository.findByHarpId(harpId)).thenReturn(Optional.empty());

    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);

    assertThat(details.getHarpId(), is(harpId));
    assertNull(details.getEmail());
  }

  @Test
  void updateUsersFromHarpReturnsEmptyWhenNoIdsProvided() {
    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(Collections.emptyList());

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verifyNoInteractions(harpProxyService);
    verifyNoInteractions(customMadieUserRepository);
  }

  @Test
  void updateUsersFromHarpReturnsEmptyWhenNullIdsProvided() {
    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(null);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verifyNoInteractions(harpProxyService);
  }

  @Test
  void updateUsersFromHarpHandlesTokenRetrievalFailure() {
    List<String> harpIds = List.of("user1", "user2");
    when(harpProxyService.getToken()).thenThrow(new RuntimeException("Token error"));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verify(harpProxyService).getToken();
    verifyNoInteractions(customMadieUserRepository);
  }

  @Test
  void updateUsersFromHarpHandlesUserDetailsFetchFailure() {
    List<String> harpIds = List.of("user1", "user2");
    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString()))
        .thenThrow(new RuntimeException("Fetch error"));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), containsInAnyOrder("user1", "user2"));
    verify(customMadieUserRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpSuccessfullyUpdatesUsers() {
    List<String> harpIds = List.of("harper");

    // Setup user details
    UserDetail detail =
        UserDetail.builder()
            .username("harper")
            .email("harper@example.com")
            .firstname("Harper")
            .lastname("Lees")
            .displayname("Harper Lee")
            .createdate("2025-10-29 13:48:37")
            .updatedate("2025-11-15 10:30:00")
            .build();
    UserDetailsResponse detailsResponse = new UserDetailsResponse();
    detailsResponse.setUserdetails(List.of(detail));

    // Setup user roles
    UserRole userRole =
        UserRole.builder()
            .programName("MADiE")
            .status("active")
            .displayName("Admin")
            .roleType("ADMIN")
            .build();
    UserRolesResponse rolesResponse = new UserRolesResponse();
    rolesResponse.setUserRoles(List.of(userRole));

    // Setup existing user
    MadieUser existingUser =
        MadieUser.builder()
            .harpId("harper")
            .email("old@example.com")
            .firstName("Old")
            .lastName("Name")
            .roles(new ArrayList<>())
            .status(UserStatus.DEACTIVATED)
            .build();

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("harper"), anyString())).thenReturn(rolesResponse);
    when(madieUserRepository.findByHarpId("harper")).thenReturn(Optional.of(existingUser));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("harper"));
    assertThat(results.getFailedHarpIds(), empty());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(customMadieUserRepository).updateMadieUser(updatesCaptor.capture(), eq("harper"));

    Map<String, Object> updates = updatesCaptor.getValue();
    assertThat(updates.get("email"), is("harper@example.com"));
    assertThat(updates.get("firstName"), is("Harper"));
    assertThat(updates.get("lastName"), is("Lees"));
    assertThat(updates.get("status"), is(UserStatus.ACTIVE));
    HarpRole roles = ((List<HarpRole>) updates.get("roles")).get(0);
    assertThat(roles.getRole(), is("Admin"));
  }

  @Test
  void updateUsersFromHarpHandlesNoUserDetailsReturned() {
    List<String> harpIds = List.of("user1");
    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(null);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    assertThat(results.getFailedHarpIds(), empty());
    verify(customMadieUserRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpHandlesEmptyUserDetailsResponse() {
    List<String> harpIds = List.of("user1");
    UserDetailsResponse emptyResponse = new UserDetailsResponse();
    emptyResponse.setUserdetails(Collections.emptyList());

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(emptyResponse);

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), empty());
    verify(customMadieUserRepository, never()).updateMadieUser(anyMap(), anyString());
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

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
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

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("inactive"), anyString())).thenReturn(rolesResponse);
    when(madieUserRepository.findByHarpId("inactive")).thenReturn(Optional.of(existingUser));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("inactive"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(customMadieUserRepository).updateMadieUser(updatesCaptor.capture(), eq("inactive"));

    Map<String, Object> updates = updatesCaptor.getValue();
    assertThat(updates.get("status"), is(UserStatus.DEACTIVATED));
    assertThat((List<?>) updates.get("roles"), empty());
  }

  @Test
  void updateUsersFromHarpTracksUnchangedUsersWhenNoUpdatesNeeded() {
    List<String> harpIds = List.of("unchanged");

    UserDetail detail =
        UserDetail.builder()
            .username("unchanged")
            .email("same@example.com")
            .firstname("Same")
            .lastname("User")
            .displayname("Same User")
            .build();
    UserDetailsResponse detailsResponse = new UserDetailsResponse();
    detailsResponse.setUserdetails(List.of(detail));

    UserRole userRole =
        UserRole.builder()
            .programName("MADiE")
            .status("active")
            .displayName("Role1")
            .roleType("TYPE1")
            .build();
    UserRolesResponse rolesResponse = new UserRolesResponse();
    rolesResponse.setUserRoles(List.of(userRole));

    HarpRole existingRole = HarpRole.builder().role("Role1").roleType("TYPE1").build();
    MadieUser existingUser =
        MadieUser.builder()
            .harpId("unchanged")
            .email("same@example.com")
            .firstName("Same")
            .lastName("User")
            .displayName("Same User")
            .status(UserStatus.ACTIVE)
            .roles(List.of(existingRole))
            .build();

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("unchanged"), anyString())).thenReturn(rolesResponse);
    when(madieUserRepository.findByHarpId("unchanged")).thenReturn(Optional.of(existingUser));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUnchangedHarpIds(), hasItem("unchanged"));
    assertThat(results.getUpdatedHarpIds(), empty());
    verify(customMadieUserRepository, never()).updateMadieUser(anyMap(), anyString());
  }

  @Test
  void updateUsersFromHarpAddsNewRolesToExistingUser() {
    List<String> harpIds = List.of("user");

    UserDetail detail =
        UserDetail.builder()
            .username("user")
            .email("user@example.com")
            .firstname("Test")
            .lastname("User")
            .displayname("Test User")
            .createdate("2025-10-29 13:48:37")
            .updatedate("2025-10-29 13:48:37")
            .build();
    UserDetailsResponse detailsResponse = new UserDetailsResponse();
    detailsResponse.setUserdetails(List.of(detail));

    UserRole role1 =
        UserRole.builder()
            .programName("MADiE")
            .status("active")
            .displayName("Role1")
            .roleType("TYPE1")
            .build();
    UserRole role2 =
        UserRole.builder()
            .programName("MADiE")
            .status("active")
            .displayName("Role2")
            .roleType("TYPE2")
            .build();
    UserRolesResponse rolesResponse = new UserRolesResponse();
    rolesResponse.setUserRoles(List.of(role1, role2));

    HarpRole existingRole = HarpRole.builder().role("Role1").roleType("TYPE1").build();
    MadieUser existingUser =
        MadieUser.builder()
            .harpId("user")
            .email("user@example.com")
            .firstName("Test")
            .lastName("User")
            .displayName("Test User")
            .status(UserStatus.ACTIVE)
            .roles(new ArrayList<>(List.of(existingRole)))
            .build();

    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    when(harpProxyService.fetchUserDetails(eq(harpIds), anyString())).thenReturn(detailsResponse);
    when(harpProxyService.fetchUserRoles(eq("user"), anyString())).thenReturn(rolesResponse);
    when(madieUserRepository.findByHarpId("user")).thenReturn(Optional.of(existingUser));

    UserUpdatesJobResultDto results = userService.updateUsersFromHarp(harpIds);

    assertThat(results.getUpdatedHarpIds(), hasItem("user"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(customMadieUserRepository).updateMadieUser(updatesCaptor.capture(), eq("user"));

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
    UserRolesResponse response =
        UserRolesResponse.builder()
            .userRoles(List.of(UserRole.builder().programName(programName).build()))
            .build();
    // when
    var status = userService.getStatusForRoles(response);
    // then
    assertThat(
        "Status should be ACTIVE when matching role exists",
        status,
        is(gov.cms.madie.models.access.UserStatus.ACTIVE));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenNoRoles() {
    // given
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    UserRolesResponse response = UserRolesResponse.builder().userRoles(List.of()).build();
    // when
    var status = userService.getStatusForRoles(response);
    // then
    assertThat(
        "Status should be DEACTIVATED when no roles",
        status,
        is(gov.cms.madie.models.access.UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenNoMatchingProgramName() {
    // given
    when(harpConfig.getProgramName()).thenReturn("MADiE");
    UserRolesResponse response =
        UserRolesResponse.builder()
            .userRoles(List.of(UserRole.builder().programName("OTHER").build()))
            .build();
    // when
    var status = userService.getStatusForRoles(response);
    // then
    assertThat(
        "Status should be DEACTIVATED when no matching program name",
        status,
        is(gov.cms.madie.models.access.UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenResponseIsNull() {
    // given
    // when
    var status = userService.getStatusForRoles(null);
    // then
    assertThat(
        "Status should be DEACTIVATED when response is null",
        status,
        is(gov.cms.madie.models.access.UserStatus.DEACTIVATED));
  }

  @Test
  void getStatusForRolesReturnsDeactivatedWhenUserRolesIsNull() {
    // given
    UserRolesResponse response = UserRolesResponse.builder().userRoles(null).build();
    // when
    var status = userService.getStatusForRoles(response);
    // then
    assertThat(
        "Status should be DEACTIVATED when userRoles is null",
        status,
        is(gov.cms.madie.models.access.UserStatus.DEACTIVATED));
  }
}
