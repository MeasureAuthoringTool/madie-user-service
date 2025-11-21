package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
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
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

  @Mock private UpdateUserJobScheduler updateUserJobScheduler;
  @Mock private Principal principal;

  @InjectMocks private AdminController adminController;

  @BeforeEach
  void setUp() {
    when(principal.getName()).thenReturn("testUser");
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
    ResponseEntity<UserUpdatesJobResultDto> response = adminController.refreshAllUsers(principal);
    // then
    assertThat(response.getStatusCode().value(), is(200));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getFailedHarpIds(), is(resultsDto.getFailedHarpIds()));
    assertThat(response.getBody().getUpdatedHarpIds(), is(resultsDto.getUpdatedHarpIds()));
  }
}
