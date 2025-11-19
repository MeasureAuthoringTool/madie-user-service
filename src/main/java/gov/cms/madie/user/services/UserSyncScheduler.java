package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.dto.SyncJobResultsDto;
import gov.cms.madie.user.repositories.MadieUserRepository;
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
public class UserSyncScheduler {

  private final MadieUserRepository madieUserRepository;
  private final UserService userService;

  /**
   * Scheduled job that syncs all user data from HARP. Runs every 5 minutes by default (configurable
   * via application.yaml).
   */
  @Scheduled(cron = "${user.sync.cron:0 */5 * * * *}")
  public SyncJobResultsDto syncAllUsersFromHarp() {
    log.info("Starting scheduled user sync from HARP at {}", Instant.now());

    int pageSize = 50;
    int pageNumber = 0;
    Page<MadieUser> userPage;
    SyncJobResultsDto syncJobResultsDto = new SyncJobResultsDto();
    do {
      // Fetch users with pagination and projection (only harpId field)
      Pageable pageable = PageRequest.of(pageNumber, pageSize);
      userPage = madieUserRepository.findAllHarpIds(pageable);

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

      SyncJobResultsDto jobResultsDto = userService.updateUsersFromHarp(harpIds);
      // collect results
      if (!CollectionUtils.isEmpty(jobResultsDto.getFailedHarpIds())) {
        log.info("Sync failed for following users: {}", jobResultsDto.getFailedHarpIds());
        syncJobResultsDto.getFailedHarpIds().addAll(jobResultsDto.getFailedHarpIds());
      }
      if (!CollectionUtils.isEmpty(jobResultsDto.getUpdatedHarpIds())) {
        syncJobResultsDto.getUpdatedHarpIds().addAll(jobResultsDto.getUpdatedHarpIds());
      }
      if (!CollectionUtils.isEmpty(jobResultsDto.getUnchangedHarpIds())) {
        syncJobResultsDto.getUnchangedHarpIds().addAll(jobResultsDto.getUnchangedHarpIds());
      }
      // Update page number
      pageNumber++;
    } while (userPage.hasNext());

    log.info(
        """
        Completed scheduled user sync from HARP at {}
        Total users updated successfully: {}\s
        Total users failed to update: {}""",
        Instant.now(),
        syncJobResultsDto.getUpdatedHarpIds().size(),
        syncJobResultsDto.getFailedHarpIds().size());
    return syncJobResultsDto;
  }

  /** Manual trigger sync job */
  public SyncJobResultsDto triggerManualSync() {
    log.info("Manual user sync triggered...");
    SyncJobResultsDto syncJobResultsDto = syncAllUsersFromHarp();
    log.info("Manual user sync completed");
    return syncJobResultsDto;
  }
}
