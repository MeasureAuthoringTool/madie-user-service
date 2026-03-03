package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import gov.cms.madie.user.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final UpdateUserJobScheduler updateUserJobScheduler;
  private final UserService userService;

  @PutMapping("/users/refresh")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Object> refreshAllUsers(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestBody(required = false) List<String> harpIds) {
    log.info("User [{}] - Kicked off refresh job", principal.getName());

    // Fire and forget - trigger job asynchronously
    updateUserJobScheduler.triggerUpdateUsersJobManually(harpIds);

    return ResponseEntity.accepted().body("User refresh job accepted");
  }

  @GetMapping("/users/last-login")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<Object> getLastLogin(
      HttpServletRequest request, @Value("${admin-api-key}") String apiKey, Principal principal) {
    log.info("User [{}] - Requested last login times for all users", principal.getName());

    List<UserLoginDto> lastLoginTimes = userService.getAllMadieUsers();

    return ResponseEntity.ok(lastLoginTimes);
  }
}
