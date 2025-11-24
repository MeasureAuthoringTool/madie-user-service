package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserJobSchedulerTest {

  @Mock private UserRepository userRepository;
  @Mock private UserService userService;

  @InjectMocks private UpdateUserJobScheduler updateUserJobScheduler;

  @Test
  void testUpdateAllUsersFromHarp() {
    Page<MadieUser> firstPage =
        new PageImpl<>(List.of(madieUser("H1"), madieUser("H2")), PageRequest.of(0, 50), 100);
    Page<MadieUser> secondPage =
        new PageImpl<>(List.of(madieUser("H3")), PageRequest.of(1, 50), 100);

    when(userRepository.findAllHarpIds(any(Pageable.class))).thenReturn(firstPage, secondPage);

    UserUpdatesJobResultDto firstBatchResult =
        UserUpdatesJobResultDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("H1")))
            .failedHarpIds(new ArrayList<>(List.of("H2")))
            .build();
    UserUpdatesJobResultDto secondBatchResult =
        UserUpdatesJobResultDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("H3")))
            .failedHarpIds(new ArrayList<>())
            .build();

    when(userService.updateUsersFromHarp(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> harpIds = invocation.getArgument(0);
              return harpIds.contains("H3") ? secondBatchResult : firstBatchResult;
            });

    UserUpdatesJobResultDto actualResults =
        updateUserJobScheduler.triggerUpdateUsersJobManually(null);

    ArgumentCaptor<List<String>> harpIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(userService, times(2)).updateUsersFromHarp(harpIdsCaptor.capture());

    assertThat(harpIdsCaptor.getAllValues().get(0), contains("H1", "H2"));
    assertThat(harpIdsCaptor.getAllValues().get(1), contains("H3"));
    assertThat(actualResults.getUpdatedHarpIds(), contains("H1", "H3"));
    assertThat(actualResults.getFailedHarpIds(), contains("H2"));
    verify(userRepository, times(2)).findAllHarpIds(any(Pageable.class));
  }

  @Test
  void testUpdateUsersForHarpIdsFromHarp() {
    List<String> harpIds = List.of("H1", "H2", "H3");
    UserUpdatesJobResultDto resultDto =
        UserUpdatesJobResultDto.builder()
            .updatedHarpIds(new ArrayList<>(List.of("H1", "H3")))
            .failedHarpIds(new ArrayList<>(List.of("H2")))
            .build();
    when(userService.updateUsersFromHarp(anyList())).thenReturn(resultDto);

    UserUpdatesJobResultDto actualResults =
        updateUserJobScheduler.triggerUpdateUsersJobManually(harpIds);

    assertThat(actualResults.getUpdatedHarpIds(), contains("H1", "H3"));
    assertThat(actualResults.getFailedHarpIds(), contains("H2"));
  }

  @Test
  void testUpdateAllUsersFromHarpWhenNoMadieUsersFound() {
    Page<MadieUser> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0);
    when(userRepository.findAllHarpIds(any(Pageable.class))).thenReturn(emptyPage);

    UserUpdatesJobResultDto actualResults =
        updateUserJobScheduler.triggerUpdateUsersJobManually(null);

    assertThat(actualResults.getUpdatedHarpIds(), empty());
    assertThat(actualResults.getFailedHarpIds(), empty());
    verify(userService, never()).updateUsersFromHarp(anyList());
  }

  @Test
  void triggerManualUpdateUserDelegatesToScheduledJob() {
    UpdateUserJobScheduler schedulerSpy =
        spy(new UpdateUserJobScheduler(userRepository, userService));
    UserUpdatesJobResultDto expectedResult = UserUpdatesJobResultDto.builder().build();
    doReturn(expectedResult).when(schedulerSpy).triggerUpdateUsersJobManually(null);

    UserUpdatesJobResultDto actualResults = schedulerSpy.triggerUpdateUsersJobManually(null);

    assertThat(actualResults, is(expectedResult));
    verify(schedulerSpy).triggerUpdateUsersJobManually(null);
  }

  private MadieUser madieUser(String harpId) {
    return MadieUser.builder().harpId(harpId).build();
  }
}
