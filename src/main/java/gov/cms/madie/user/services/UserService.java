package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.HarpResponseWrapper;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserRole;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.repositories.UserRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final CacheManager cacheManager;
  private final TokenManager tokenManager;
  private final UserRepository userRepository;
  private final HarpProxyService harpProxyService;
  private final HarpConfig harpConfig;

  public MadieUser getUserByHarpId(String harpId) {
    // TODO: replace with database lookup
    return MadieUser.builder().harpId(harpId).build();
  }

  public MadieUser refreshUserRolesAndLogin(String harpId) {
    TokenResponse token = tokenManager.getCurrentToken();
    MadieUser.MadieUserBuilder madieUserBuilder = MadieUser.builder().harpId(harpId);
    if (token == null || StringUtils.isBlank(token.getAccessToken())) {
      // bad things happened
      log.info("Unable to refresh user roles for HARP ID: {} - no token received", harpId);
      // for now, do not block user login
    } else {
      HarpResponseWrapper<UserRolesResponse> responseWrapper =
          harpProxyService.fetchUserRoles(harpId, token.getAccessToken());
      UserRolesResponse userRolesResponse = responseWrapper.getResponse();
      return userRepository.loginUser(
          MadieUser.builder()
              .harpId(harpId)
              .accessStartAt(getMostRecentStartDate(userRolesResponse))
              .status(getStatusForRoles(responseWrapper))
              .roles(
                  userRolesResponse.getUserRoles().stream()
                      .map(
                          role ->
                              HarpRole.builder()
                                  .role(role.getDisplayName())
                                  .roleType(role.getRoleType())
                                  .build())
                      .toList())
              .build());
    }
    return madieUserBuilder.build();
  }

  @Cacheable("users")
  public UserDetailsDto getUserDetailsByHarpId(String harpId) {
    // TODO: fetch user record and map to UserDetailsDto
    return UserDetailsDto.builder().harpId(harpId).build();
  }

  /**
   * Determines the UserStatus based on the response from the HARP API when fetching user roles. It checks for specific error codes and messages to determine if the user is deactivated or suspended, and otherwise checks if the user has an active role in the specified program.
   *
   * @param responseWrapper the response wrapper containing the UserRolesResponse and any exceptions
   * @return
   */
  public UserStatus getStatusForRoles(HarpResponseWrapper<UserRolesResponse> responseWrapper) {
    if (responseWrapper == null || !responseWrapper.isSuccess()) {
      if (responseWrapper != null && responseWrapper.getError() != null &&
          "ERR-ROLECREATION-027".equals(responseWrapper.getError().getErrorCode())) {
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
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    return userRolesResponse.getUserRoles().stream()
        .map(UserRole::getStartDate)
        .filter(Objects::nonNull)
        .map(
            dateStr -> {
              try {
                return LocalDateTime.parse(dateStr, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
              } catch (Exception e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }
}
