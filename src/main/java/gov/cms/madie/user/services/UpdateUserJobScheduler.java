package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.repositories.UserRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateUserJobScheduler {

  private final UserRepository userRepository;
  private final UserService userService;

  /** Scheduled job that updates all user data from HARP. */
  @Scheduled(cron = "${user.update.cron-expression}")
  public UserUpdatesJobResultDto triggerUpdateUsersJob() {
    log.info("Starting user update job from HARP at {}", Instant.now());

    int pageSize = 50;
    int pageNumber = 0;
    Page<MadieUser> userPage;
    UserUpdatesJobResultDto updateJobResultsDto = new UserUpdatesJobResultDto();
    do {
      // Fetch users with pagination and projection (only harpId field)
      Pageable pageable = PageRequest.of(pageNumber, pageSize);
      userPage = userRepository.findAllHarpIds(pageable);

      if (userPage.isEmpty()) {
        log.info("No users found on page {}", pageNumber);
        break;
      }

      log.info(
          "Processing page {} of {}: {} users",
          pageNumber + 1,
          userPage.getTotalPages(),
          userPage.getNumberOfElements());

      // Extract HARP IDs from current page
      List<String> harpIds =
          userPage.getContent().stream()
              .map(MadieUser::getHarpId)
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.toList());

      UserUpdatesJobResultDto resultsDto = userService.updateUsersFromHarp(harpIds);
      // collect results
      if (!CollectionUtils.isEmpty(resultsDto.getFailedHarpIds())) {
        updateJobResultsDto.getFailedHarpIds().addAll(resultsDto.getFailedHarpIds());
      }
      if (!CollectionUtils.isEmpty(resultsDto.getUpdatedHarpIds())) {
        updateJobResultsDto.getUpdatedHarpIds().addAll(resultsDto.getUpdatedHarpIds());
      }
      // Update page number
      pageNumber++;
    } while (userPage.hasNext());

    logJobResults(updateJobResultsDto);
    return updateJobResultsDto;
  }

  /** update job manual trigger */
  @Async
  public void triggerUpdateUsersJobManually(List<String> harpIds) {
    log.info("Manual user update job triggered at {}", Instant.now());
    // if no harpIds provided, update all users
    if (CollectionUtils.isEmpty(harpIds)) {
      triggerUpdateUsersJob();
    } else {
      UserUpdatesJobResultDto resultsDto;
      if (userService.areHarpIdsValid(harpIds)) {
        resultsDto = userService.updateUsersFromHarp(harpIds);
      } else {
        log.warn("Invalid HARP IDs provided for manual user update: {}", harpIds);
        resultsDto =
            UserUpdatesJobResultDto.builder()
                .failedHarpIds(harpIds)
                .updatedHarpIds(List.of())
                .build();
      }
      logJobResults(resultsDto);
    }
    log.info("Manual user update completed");
  }

  private void logJobResults(UserUpdatesJobResultDto resultsDto) {
    log.info(
        """
      User update job results:
      Total users updated successfully: {}
      Total users failed to update: {}
      Failed HARP IDs: {}""",
        resultsDto.getUpdatedHarpIds().size(),
        resultsDto.getFailedHarpIds().size(),
        resultsDto.getFailedHarpIds());
  }
}
