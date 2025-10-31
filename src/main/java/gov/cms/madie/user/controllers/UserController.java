package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.dto.DetailsRequestDto;
import gov.cms.madie.user.dto.UserDetailsDto;
import gov.cms.madie.user.services.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {

  private UserService userService;

  @GetMapping("/{harpId}")
  public ResponseEntity<MadieUser> getUser(@PathVariable String harpId, Principal principal) {
    log.info("User [{}] - Getting user with HARP ID: {}", principal.getName(), harpId);
    MadieUser user = userService.getUserByHarpId(harpId);
    return ResponseEntity.ok(user);
  }

  @PutMapping("/{harpId}")
  public ResponseEntity<MadieUser> updateUser(@PathVariable String harpId, Principal principal) {
    log.info("User [{}] - Updating user with HARP ID: {}", principal.getName(), harpId);
    MadieUser user = userService.refreshUserDetailsAndLogin(harpId);
    return ResponseEntity.ok(user);
  }

  @GetMapping("/activity")
  public ResponseEntity<Object> getUserActivityReport(Principal principal) {
    log.info("User [{}] - Generating user activity report", principal.getName());
    return ResponseEntity.ok("User report coming soon");
  }

  @PutMapping("/all-users-refresh")
  public ResponseEntity<Object> refreshAllUsers(Principal principal) {
    log.info("User [{}] - Kicked off refresh of all users", principal.getName());
    return ResponseEntity.accepted().body("User refresh job accepted");
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
                    harpId -> harpId, harpId -> userService.getUserDetailsByHarpId(harpId)));
    return ResponseEntity.ok(userDetailsMap);
  }
}
