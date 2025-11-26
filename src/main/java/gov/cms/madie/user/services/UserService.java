package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.repositories.UserRepository;
import io.micrometer.common.util.StringUtils;
import gov.cms.madie.user.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final TokenManager tokenManager;
  private final UserRepository userRepository;
  private final HarpProxyService harpProxyService;
  private final HarpConfig harpConfig;

  public boolean areHarpIdsValid(List<String> harpIds) {
    if (CollectionUtils.isEmpty(harpIds)) {
      return false;
    }

    return userRepository.countByHarpIdIn(harpIds) == harpIds.size();
  }

  public MadieUser getUserByHarpId(String harpId) {
    return userRepository
        .findByHarpId(harpId)
        .orElseGet(
            () -> {
              log.warn("User not found in database for HARP ID: {}", harpId);
              return MadieUser.builder().harpId(harpId).build();
            });
  }

  public MadieUser refreshUserRolesAndLogin(String harpId) {
    TokenResponse token = tokenManager.getCurrentToken();
    MadieUser.MadieUserBuilder madieUserBuilder = MadieUser.builder().harpId(harpId);
    if (token == null || StringUtils.isBlank(token.getAccessToken())) {
      log.info("Unable to refresh user roles for HARP ID: {} - no token received", harpId);
    } else {
      HarpResponseWrapper<UserRolesResponse> responseWrapper =
          harpProxyService.fetchUserRoles(harpId, token.getAccessToken());
      return userRepository.loginUser(buildMadieUser(null, responseWrapper));
    }
    return madieUserBuilder.build();
  }

  private List<HarpRole> harpRolesToMadieRoleList(UserRolesResponse userRolesResponse) {
    String programName = harpConfig.getProgramName();
    if (userRolesResponse == null || userRolesResponse.getUserRoles() == null) {
      return List.of();
    }
    return userRolesResponse.getUserRoles().stream()
        .filter(
            userRole ->
                programName.equalsIgnoreCase(userRole.getProgramName())
                    && "Active".equalsIgnoreCase(userRole.getStatus()))
        .map(
            role ->
                HarpRole.builder().role(role.getDisplayName()).roleType(role.getRoleType()).build())
        .toList();
  }

  @Cacheable("users")
  public UserDetailsDto getUserDetailsByHarpId(String harpId) {
    return userRepository
        .findByHarpId(harpId)
        .map(
            user ->
                UserDetailsDto.builder()
                    .harpId(user.getHarpId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build())
        .orElseGet(
            () -> {
              log.warn("User details not found for HARP ID: {}", harpId);
              return UserDetailsDto.builder().harpId(harpId).build();
            });
  }

  /**
   * Updates users in the database with fresh data from HARP.
   *
   * @param harpIds List of HARP IDs to update
   * @return UserUpdatesJobResultDto containing the results of the update operation
   */
  public UserUpdatesJobResultDto updateUsersFromHarp(List<String> harpIds) {
    UserUpdatesJobResultDto result = new UserUpdatesJobResultDto();

    if (CollectionUtils.isEmpty(harpIds)) {
      log.warn("No valid HARP IDs provided. Aborting user update job.");
      return result;
    }

    TokenResponse token = fetchTokenOrFail();
    if (token == null) {
      log.info("Unable to fetch the token. Aborting user update job.");
      return result;
    }

    UserDetailsResponse detailsResponse = fetchUserDetailsOrFail(harpIds, token, result);
    if (detailsResponse == null || CollectionUtils.isEmpty(detailsResponse.getUserdetails())) {
      log.warn("No user details returned from HARP for batch");
      return result;
    }

    Map<String, UserDetail> detailsMap =
        detailsResponse.getUserdetails().stream()
            .collect(
                Collectors.toMap(
                    detail -> detail.getUsername().toLowerCase(Locale.ROOT),
                    detail -> detail,
                    (existing, replacement) -> existing));
    for (String harpId : harpIds) {
      updateSingleUser(harpId, token, detailsMap, result);
    }
    return result;
  }

  /**
   * Fetches authentication token, handling errors gracefully.
   *
   * @return TokenResponse if successful, null otherwise
   */
  private TokenResponse fetchTokenOrFail() {
    try {
      return tokenManager.getCurrentToken();
    } catch (Exception e) {
      log.error("Error obtaining HARP token. Aborting user update job.", e);
      return null;
    }
  }

  /**
   * Fetches user details from HARP, handling errors gracefully.
   *
   * @param harpIds list of HARP IDs to fetch
   * @param token authentication token
   * @param result the result object to populate on failure
   * @return UserDetailsResponse if successful, null otherwise
   */
  private UserDetailsResponse fetchUserDetailsOrFail(
      List<String> harpIds, TokenResponse token, UserUpdatesJobResultDto result) {
    try {
      return harpProxyService.fetchUserDetails(harpIds, token.getAccessToken());
    } catch (Exception e) {
      log.error("Error fetching HARP user details. Aborting user update job.", e);
      result.getFailedHarpIds().addAll(harpIds);
      return null;
    }
  }

  /**
   * Updates a single user with fresh data from HARP.
   *
   * @param harpId the HARP ID of the user to update
   * @param token authentication token
   * @param detailsMap map of user details
   * @param result the result object to populate
   */
  private void updateSingleUser(
      String harpId,
      TokenResponse token,
      Map<String, UserDetail> detailsMap,
      UserUpdatesJobResultDto result) {
    try {
      HarpResponseWrapper<UserRolesResponse> responseWrapper =
          harpProxyService.fetchUserRoles(harpId, token.getAccessToken());
      MadieUser updatedUser =
          buildMadieUser(detailsMap.get(harpId.toLowerCase(Locale.ROOT)), responseWrapper);

      if (updatedUser == null) {
        log.warn("No user data returned from HARP for HARP ID: {}", harpId);
        result.getFailedHarpIds().add(harpId);
        return;
      }

      MadieUser existingUser =
          userRepository.findByHarpId(harpId).orElse(MadieUser.builder().harpId(harpId).build());
      Map<String, Object> updates = prepareUpdate(existingUser, updatedUser);

      if (!CollectionUtils.isEmpty(updates)) {
        userRepository.updateMadieUser(updates, harpId);
        result.getUpdatedHarpIds().add(harpId);
      }

    } catch (Exception e) {
      log.error("Failed to update user with HARP ID: {}", harpId, e);
      result.getFailedHarpIds().add(harpId);
    }
  }

  /* package-private for testability */
  MadieUser buildMadieUser(
      UserDetail detail, HarpResponseWrapper<UserRolesResponse> responseWrapper) {
    MadieUser.MadieUserBuilder userBuilder = MadieUser.builder();
    if (detail != null) {
      userBuilder
          .email(detail.getEmail())
          .firstName(detail.getFirstname())
          .lastName(detail.getLastname())
          .displayName(detail.getDisplayname())
          .createdAt(convertoInstant(detail.getCreatedate()))
          .lastModifiedAt(convertoInstant(detail.getUpdatedate()));
    }
    if (responseWrapper == null || !responseWrapper.isSuccess()) {
      if (responseWrapper != null
          && responseWrapper.getError() != null
          && "ERR-ROLECREATION-027".equals(responseWrapper.getError().getErrorCode())) {
        userBuilder.status(UserStatus.DEACTIVATED).roles(List.of());
      } else {
        userBuilder.status(UserStatus.ERROR_SUSPENDED).roles(List.of());
      }
      return userBuilder.build();
    }
    UserRolesResponse rolesResponse = responseWrapper.getResponse();
    List<HarpRole> roles = harpRolesToMadieRoleList(rolesResponse);
    if (!CollectionUtils.isEmpty(roles)) {
      userBuilder
          .status(UserStatus.ACTIVE)
          .roles(roles)
          .accessStartAt(getMostRecentStartDate(rolesResponse));
    } else {
      userBuilder.status(UserStatus.DEACTIVATED).roles(List.of());
    }
    return userBuilder.build();
  }

  // Prepare a map of fields to update based on differences between existing and updated user
  private Map<String, Object> prepareUpdate(MadieUser madieUser, MadieUser updatedUser) {
    if (madieUser == null || updatedUser == null) {
      return null;
    }

    Map<String, Object> updates = new HashMap<>();

    if (!Objects.equals(madieUser.getEmail(), updatedUser.getEmail())) {
      updates.put("email", updatedUser.getEmail());
    }
    if (!Objects.equals(madieUser.getFirstName(), updatedUser.getFirstName())) {
      updates.put("firstName", updatedUser.getFirstName());
    }
    if (!Objects.equals(madieUser.getLastName(), updatedUser.getLastName())) {
      updates.put("lastName", updatedUser.getLastName());
    }
    if (!Objects.equals(madieUser.getDisplayName(), updatedUser.getDisplayName())) {
      updates.put("displayName", updatedUser.getDisplayName());
    }
    if (madieUser.getStatus() != updatedUser.getStatus()) {
      updates.put("status", updatedUser.getStatus());
    }
    if (madieUser.getCreatedAt() == null) {
      updates.put("createdAt", Instant.now());
    }
    updates.put("lastModifiedAt", Instant.now());
    updates.put("roles", updatedUser.getRoles());

    return updates;
  }

  private Instant convertoInstant(String dateTimeStr) {
    if (StringUtils.isBlank(dateTimeStr)) {
      return null;
    }
    try {
      return Instant.from(
          LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
              .atZone(ZoneId.systemDefault())
              .toInstant());
    } catch (Exception e) {
      log.error("Error converting date string to Instant: {}", dateTimeStr, e);
      return null;
    }
  }

  /**
   * Determines the UserStatus based on the response from the HARP API when fetching user roles. It
   * checks for specific error codes and messages to determine if the user is deactivated or
   * suspended, and otherwise checks if the user has an active role in the specified program.
   *
   * @param responseWrapper the response wrapper containing the UserRolesResponse and any exceptions
   * @return UserStatus.ACTIVE if user has active roles, UserStatus.DEACTIVATED otherwise
   */
  public UserStatus getStatusForRoles(HarpResponseWrapper<UserRolesResponse> responseWrapper) {
    if (responseWrapper == null || !responseWrapper.isSuccess()) {
      if (responseWrapper != null
          && responseWrapper.getError() != null
          && "ERR-ROLECREATION-027".equals(responseWrapper.getError().getErrorCode())) {
        return UserStatus.DEACTIVATED;
      } else {
        return UserStatus.ERROR_SUSPENDED;
      }
    }
    UserRolesResponse userRolesResponse = responseWrapper.getResponse();
    if (userRolesResponse == null || userRolesResponse.getUserRoles() == null) {
      return UserStatus.DEACTIVATED;
    }
    String programName = harpConfig.getProgramName();
    boolean hasActiveRole =
        userRolesResponse.getUserRoles().stream()
            .anyMatch(role -> programName.equals(role.getProgramName()));
    return hasActiveRole ? UserStatus.ACTIVE : UserStatus.DEACTIVATED;
  }

  /**
   * Utility method to get the most recent startDate from a UserRolesResponse.
   *
   * @param userRolesResponse the response containing user roles
   * @return the most recent startDate, or null if no roles
   */
  public Instant getMostRecentStartDate(UserRolesResponse userRolesResponse) {
    if (userRolesResponse == null
        || userRolesResponse.getUserRoles() == null
        || userRolesResponse.getUserRoles().isEmpty()) {
      return null;
    }
    return userRolesResponse.getUserRoles().stream()
        .map(UserRole::getStartDate)
        .filter(java.util.Objects::nonNull)
        .map(this::convertoInstant)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }
}
