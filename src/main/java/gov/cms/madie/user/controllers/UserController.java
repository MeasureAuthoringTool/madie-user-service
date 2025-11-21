package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

  @Value("${harp.test.override-id:}")
  private String harpOverrideTestId;

  @Autowired private UserService userService;

  @GetMapping("/{harpId}")
  public ResponseEntity<MadieUser> getUser(@PathVariable String harpId, Principal principal) {
    log.info("User [{}] - Getting user with HARP ID: {}", principal.getName(), harpId);
    MadieUser user = userService.getUserByHarpId(harpId);
    return ResponseEntity.ok(user);
  }

  @PutMapping("/{harpId}")
  public ResponseEntity<MadieUser> updateUser(@PathVariable String harpId, Principal principal) {
    log.info("User [{}] - Updating user with HARP ID: {}", principal.getName(), harpId);
    if (!principal.getName().equals(harpId) && StringUtils.isBlank(harpOverrideTestId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          String.format(
              "User [%s] attempted to update user [%s] - not allowed",
              principal.getName(), harpId));
    }
    MadieUser user =
        userService.refreshUserRolesAndLogin(
            StringUtils.isBlank(harpOverrideTestId) ? harpId : harpOverrideTestId);
    return ResponseEntity.ok(user);
  }

  @GetMapping("/activity")
  public ResponseEntity<Object> getUserActivityReport(Principal principal) {
    log.info("User [{}] - Generating user activity report", principal.getName());
    // TODO: implement activity report. Add support for optional days parameter
    // Generate user activity report based on days, based on more recent of lastLoginAt or
    // accessStartAt
    return ResponseEntity.ok("User report coming soon");
  }

  @GetMapping("/{harpId}/details")
  public ResponseEntity<UserDetailsDto> getUserDetails(
      @PathVariable String harpId, Principal principal) {
    log.info("User [{}] - Getting user details for HARP ID: {}", principal.getName(), harpId);
    UserDetailsDto userDetails = userService.getUserDetailsByHarpId(harpId);
    return ResponseEntity.ok(userDetails);
  }

  @PostMapping("/details")
  public ResponseEntity<Map<String, UserDetailsDto>> getBulkUserDetails(
      @RequestBody DetailsRequestDto detailsRequest, Principal principal) {
    log.info(
        "User [{}] - Getting bulk user details for HARP IDs: {}",
        principal.getName(),
        detailsRequest.getHarpIds());
    Map<String, UserDetailsDto> userDetailsMap =
        detailsRequest.getHarpIds().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    harpId -> harpId, userService::getUserDetailsByHarpId));
    return ResponseEntity.ok(userDetailsMap);
  }
}
