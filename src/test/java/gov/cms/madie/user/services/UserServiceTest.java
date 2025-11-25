package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.HarpResponseWrapper;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserRole;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock TokenManager tokenManager;
  @Mock HarpProxyService harpProxyService;
  @Mock UserRepository userRepository;
  @Mock HarpConfig harpConfig;
  @InjectMocks private UserService userService;

  @Test
  void getUserByHarpIdReturnsMadieUser() {
    // given
    String harpId = "aaa111";
    // when
    MadieUser user = userService.getUserByHarpId(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
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
  void getUserDetailsByHarpIdReturnsUserDetailsDto() {
    // given
    String harpId = "ccc333";
    // when
    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);
    // then
    assertThat(details.getHarpId(), is(harpId));
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
}
