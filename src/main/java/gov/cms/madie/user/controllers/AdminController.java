package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.security.AdminOnly;
import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import gov.cms.madie.user.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@AdminOnly
public class AdminController {

  private final UpdateUserJobScheduler updateUserJobScheduler;
  private final UserService userService;

  @PutMapping("/users/refresh")
  public ResponseEntity<Object> refreshAllUsers(
      HttpServletRequest request,
      Principal principal,
      @RequestBody(required = false) List<String> harpIds) {
    log.info("User [{}] - Kicked off refresh job", principal.getName());

    // Fire and forget - trigger job asynchronously
    updateUserJobScheduler.triggerUpdateUsersJobManually(harpIds);

    return ResponseEntity.accepted().body("User refresh job accepted");
  }

  @GetMapping("/users/last-login")
  public ResponseEntity<Object> getLastLogin(HttpServletRequest request, Principal principal) {
    log.info("User [{}] - Requested last login times for all users", principal.getName());

    List<UserLoginDto> lastLoginTimes = userService.getAllMadieUsers();

    return ResponseEntity.ok(lastLoginTimes);
  }
}
