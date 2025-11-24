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
        log.info("Update failed for following users: {}", resultsDto.getFailedHarpIds());
        updateJobResultsDto.getFailedHarpIds().addAll(resultsDto.getFailedHarpIds());
      }
      if (!CollectionUtils.isEmpty(resultsDto.getUpdatedHarpIds())) {
        updateJobResultsDto.getUpdatedHarpIds().addAll(resultsDto.getUpdatedHarpIds());
      }
      // Update page number
      pageNumber++;
    } while (userPage.hasNext());

    log.info(
        """
        Completed scheduled user update from HARP at {}
        Total users updated successfully: {}\s
        Total users failed to update: {}""",
        Instant.now(),
        updateJobResultsDto.getUpdatedHarpIds().size(),
        updateJobResultsDto.getFailedHarpIds().size());
    return updateJobResultsDto;
  }

  /** update job manual trigger */
  public UserUpdatesJobResultDto triggerUpdateUsersJobManually(List<String> harpIds) {
    log.info("Manual user update triggered...");
    UserUpdatesJobResultDto jobResults;
    // if no harpIds provided, update all users
    if (CollectionUtils.isEmpty(harpIds)) {
      jobResults = triggerUpdateUsersJob();
    } else {
      jobResults = userService.updateUsersFromHarp(harpIds);
    }
    log.info("Manual user update completed");
    return jobResults;
  }
}
