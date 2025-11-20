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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
      UserRolesResponse userRolesResponse =
          harpProxyService.fetchUserRoles(harpId, token.getAccessToken());

      return userRepository.loginUser(
          MadieUser.builder()
              .harpId(harpId)
              .accessStartAt(getMostRecentStartDate(userRolesResponse))
              .status(getStatusForRoles(userRolesResponse))
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
   * Determines the user status based on the presence of active roles in the UserRolesResponse.
   *
   * @param userRolesResponse
   * @return
   */
  public UserStatus getStatusForRoles(UserRolesResponse userRolesResponse) {
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
    java.time.format.DateTimeFormatter formatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault());
    return userRolesResponse.getUserRoles().stream()
        .map(gov.cms.madie.user.dto.UserRole::getStartDate)
        .filter(java.util.Objects::nonNull)
        .map(
            dateStr -> {
              try {
                return java.time.LocalDateTime.parse(dateStr, formatter)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant();
              } catch (Exception e) {
                return null;
              }
            })
        .filter(java.util.Objects::nonNull)
        .max(java.util.Comparator.naturalOrder())
        .orElse(null);
  }
}
