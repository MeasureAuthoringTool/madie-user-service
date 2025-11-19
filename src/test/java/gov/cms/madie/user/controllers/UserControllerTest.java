package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.dto.SyncJobResultsDto;
import gov.cms.madie.user.services.UserService;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
  @Mock private UserService userService;
  @Mock private Principal principal;
  @Mock private UpdateUserJobScheduler userSyncScheduler;
  @InjectMocks private UserController userController;

  @BeforeEach
  void setUp() {
    when(principal.getName()).thenReturn("testUser");
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
    when(userService.refreshUserRolesAndLogin("123")).thenReturn(user);
    // when
    ResponseEntity<MadieUser> response = userController.updateUser("123", principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    assertThat(response.getBody(), is(user));
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
    SyncJobResultsDto syncJobResultsDto =
        SyncJobResultsDto.builder()
            .failedHarpIds(List.of("John"))
            .updatedHarpIds(List.of("Bob"))
            .build();
    when(userSyncScheduler.triggerUpdateUserJobManually()).thenReturn(syncJobResultsDto);
    // when
    ResponseEntity<SyncJobResultsDto> response = userController.refreshAllUsers(principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getFailedHarpIds(), is(syncJobResultsDto.getFailedHarpIds()));
    assertThat(response.getBody().getUpdatedHarpIds(), is(syncJobResultsDto.getUpdatedHarpIds()));
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
