package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController()
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final UpdateUserJobScheduler updateUserJobScheduler;

  // TODO: should this be asynchronous? its going to take a while to finish
  @PutMapping("/users/refresh")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<UserUpdatesJobResultDto> refreshAllUsers(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      Principal principal,
      @RequestBody(required = false) List<String> harpIds) {
    log.info("User [{}] - Kicked off refresh job", principal.getName());
    return ResponseEntity.ok().body(updateUserJobScheduler.triggerUpdateUsersJobManually(harpIds));
  }
}
