package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.dto.*;
import gov.cms.madie.user.repositories.CustomMadieUserRepository;
import gov.cms.madie.user.repositories.MadieUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final HarpProxyService harpProxyService;
  private final MadieUserRepository madieUserRepository;
  private final CustomMadieUserRepository customMadieUserRepository;

  public MadieUser getUserByHarpId(String harpId) {
    return madieUserRepository
        .findByHarpId(harpId)
        .orElseGet(
            () -> {
              log.warn("User not found in database for HARP ID: {}", harpId);
              return MadieUser.builder().harpId(harpId).build();
            });
  }

  public MadieUser refreshUserRolesAndLogin(String harpId) {
    TokenResponse token = harpProxyService.getToken();
    MadieUser.MadieUserBuilder madieUserBuilder = MadieUser.builder().harpId(harpId);
    if (token == null || StringUtils.isBlank(token.getAccessToken())) {
      // bad things happened
      log.info("Unable to refresh user roles for HARP ID: {} - no token received", harpId);
      // for now, do not block user login
    } else {
      log.info("refreshing with token: {}", token.getAccessToken());
      // TODO: call HARP to refresh user roles
    }
    return madieUserBuilder.build();
  }

  public UserDetailsDto getUserDetailsByHarpId(String harpId) {
    return madieUserRepository
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

  /*
   *Updates users in the database with fresh data from HARP.
   * @param harpIds List of HARP IDs to update
   */
  public UserUpdatesJobResultDto updateUsersFromHarp(List<String> harpIds) {
    UserUpdatesJobResultDto userUpdatesJobResultDto = new UserUpdatesJobResultDto();
    if (CollectionUtils.isEmpty(harpIds)) {
      log.warn("No valid HARP IDs provided. aborting sync.");
      return userUpdatesJobResultDto;
    }
    TokenResponse token;
    // Get HARP token
    try {
      token = harpProxyService.getToken();
    } catch (Exception e) {
      log.error("Error obtaining HARP token. Aborting user sync.", e);
      return userUpdatesJobResultDto;
    }

    // Fetch user details from HARP
    UserDetailsResponse detailsResponse;
    try {
      detailsResponse = harpProxyService.fetchUserDetails(harpIds, token.getAccessToken());
    } catch (Exception e) {
      log.error("Error fetching HARP user details. Aborting user sync.", e);
      userUpdatesJobResultDto.getFailedHarpIds().addAll(harpIds);
      return userUpdatesJobResultDto;
    }

    if (detailsResponse != null && !CollectionUtils.isEmpty(detailsResponse.getUserdetails())) {
      // Create a map for quick lookup
      Map<String, UserDetail> detailsMap =
          detailsResponse.getUserdetails().stream()
              .collect(
                  Collectors.toMap(
                      detail -> detail.getUsername().toLowerCase(), detail -> detail, (a, b) -> a));

      // Update each user with fresh data from HARP
      for (String harpId : harpIds) {
        try {
          UserRolesResponse rolesResponse =
              harpProxyService.fetchUserRoles(harpId, token.getAccessToken());
          MadieUser updatedUser =
              buildMadieUser(detailsMap.get(harpId.toLowerCase()), rolesResponse);
          if (updatedUser != null) {
            MadieUser existingUser =
                madieUserRepository
                    .findByHarpId(harpId)
                    .orElse(MadieUser.builder().harpId(harpId).build());
            Map<String, Object> updates = prepareUpdate(existingUser, updatedUser);
            if (CollectionUtils.isEmpty(updates)) {
              log.info("No updates required for user with HARP ID: {}", harpId);
              userUpdatesJobResultDto.getUnchangedHarpIds().add(harpId);
              continue;
            }
            customMadieUserRepository.updateMadieUser(updates, harpId);
            userUpdatesJobResultDto.getUpdatedHarpIds().add(harpId);
          } else {
            log.warn("No user data returned from HARP for HARP ID: {}", harpId);
            userUpdatesJobResultDto.getFailedHarpIds().add(harpId);
          }
        } catch (Exception e) {
          log.error("Failed to update user with HARP ID: {}", harpId, e);
          userUpdatesJobResultDto.getFailedHarpIds().add(harpId);
        }
      }
    } else {
      log.warn("No user details returned from HARP for batch");
    }

    return userUpdatesJobResultDto;
  }

  private MadieUser buildMadieUser(UserDetail detail, UserRolesResponse rolesResponse) {

    // Update user details from HARP response
    MadieUser.MadieUserBuilder userBuilder =
        MadieUser.builder()
            .email(detail.getEmail())
            .firstName(detail.getFirstname())
            .lastName(detail.getLastname())
            .displayName(detail.getDisplayname())
            .createdAt(convertoInstant(detail.getCreatedate()))
            .lastModifiedAt(convertoInstant(detail.getUpdatedate()));

    if (rolesResponse != null && !CollectionUtils.isEmpty(rolesResponse.getUserRoles())) {
      List<HarpRole> roles =
          rolesResponse.getUserRoles().stream()
              .filter(
                  userRole ->
                      "MADiE".equalsIgnoreCase(userRole.getProgramName())
                          && "active".equalsIgnoreCase(userRole.getStatus()))
              .map(
                  role ->
                      HarpRole.builder()
                          .role(role.getDisplayName())
                          .roleType(role.getRoleType())
                          .build())
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(roles)) {
        userBuilder.status(UserStatus.ACTIVE).roles(roles);
        return userBuilder.build();
      }
    }

    // No active MADiE roles found, deactivate user
    userBuilder.status(UserStatus.DEACTIVATED).roles(List.of());

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
    if (!Objects.equals(madieUser.getCreatedAt(), updatedUser.getCreatedAt())) {
      updates.put("createdAt", updatedUser.getCreatedAt());
    }
    if (!Objects.equals(madieUser.getLastModifiedAt(), updatedUser.getLastModifiedAt())) {
      updates.put("lastModifiedAt", updatedUser.getLastModifiedAt());
    }
    // Handle role updates
    if (CollectionUtils.isEmpty(updatedUser.getRoles())) {
      // if no roles from HARP, clear existing roles
      updates.put("roles", List.of());
    } else if (CollectionUtils.isEmpty(madieUser.getRoles())) {
      // if no existing roles, add all new roles
      updates.put("roles", updatedUser.getRoles());
    } else if (madieUser.getRoles().size() == 1 && updatedUser.getRoles().size() == 1) {
      // if only one role exists, and it's different, replace existing role
      if (!madieUser.getRoles().get(0).equals(updatedUser.getRoles().get(0))) {
        updates.put("roles", updatedUser.getRoles());
      }
    } else {
      // Add new roles if it doesn't exist, keep existing roles that are still valid
      List<HarpRole> existingRoles = madieUser.getRoles();
      List<HarpRole> newRoles = updatedUser.getRoles();
      List<HarpRole> rolesToKeep = existingRoles.stream().filter(newRoles::contains).toList();
      List<HarpRole> rolesToAdd =
          newRoles.stream().filter(role -> !existingRoles.contains(role)).toList();
      updates.put(
          "roles",
          Stream.of(rolesToAdd, rolesToKeep).flatMap(List::stream).collect(Collectors.toList()));
    }

    return updates;
  }

  private Instant convertoInstant(String dateTimeStr) {
    if (StringUtils.isBlank(dateTimeStr)) {
      return null;
    }
    return Instant.from(
        LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.systemDefault())
            .toInstant());
  }
}
