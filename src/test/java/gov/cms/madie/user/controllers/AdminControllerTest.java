package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

  @Mock private UpdateUserJobScheduler updateUserJobScheduler;
  @Mock private Principal principal;

  @InjectMocks private AdminController adminController;

  private HttpServletRequest request;
  private String apiKey;

  @BeforeEach
  void setUp() {
    apiKey = "test-api-key";

    // Create MockHttpServletRequest and set the api-key header
    MockHttpServletRequest mockRequest = new MockHttpServletRequest();
    mockRequest.addHeader("api-key", apiKey);
    request = mockRequest;

    when(principal.getName()).thenReturn("testUser");
  }

  @Test
  void refreshAllUsers() {
    // given
    UserUpdatesJobResultDto resultsDto =
        UserUpdatesJobResultDto.builder()
            .failedHarpIds(List.of("John"))
            .updatedHarpIds(List.of("Bob"))
            .build();
    when(updateUserJobScheduler.triggerUpdateUsersJobManually(null)).thenReturn(resultsDto);

    // when
    ResponseEntity<UserUpdatesJobResultDto> response =
        adminController.refreshAllUsers(request, apiKey, principal, null);

    // then
    assertThat(response.getStatusCode().value(), is(200));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getFailedHarpIds(), is(resultsDto.getFailedHarpIds()));
    assertThat(response.getBody().getUpdatedHarpIds(), is(resultsDto.getUpdatedHarpIds()));
  }

  @Test
  void refreshUsersForHarpIds() {
    // given
    List<String> harpIds = List.of("John", "Jane", "Bob");
    UserUpdatesJobResultDto resultsDto =
        UserUpdatesJobResultDto.builder()
            .failedHarpIds(List.of("John", "Bob"))
            .updatedHarpIds(List.of("Jane"))
            .build();
    when(updateUserJobScheduler.triggerUpdateUsersJobManually(harpIds)).thenReturn(resultsDto);

    // when
    ResponseEntity<UserUpdatesJobResultDto> response =
        adminController.refreshAllUsers(request, apiKey, principal, harpIds);

    // then
    assertThat(response.getStatusCode().value(), is(200));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getFailedHarpIds(), is(resultsDto.getFailedHarpIds()));
    assertThat(response.getBody().getUpdatedHarpIds(), is(resultsDto.getUpdatedHarpIds()));
  }
}
