package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserUpdatesJobResultDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController()
@RequestMapping("/admin")
@AllArgsConstructor
public class AdminController {

  UpdateUserJobScheduler updateUserJobScheduler;

  // TODO: should this be asynchronous? its going to take a while to finish
  @PutMapping("/users/refresh")
  public ResponseEntity<UserUpdatesJobResultDto> refreshAllUsers(Principal principal) {
    log.info("User [{}] - Kicked off refresh job", principal.getName());
    return ResponseEntity.ok().body(updateUserJobScheduler.triggerUpdateUsersJobManually());
  }
}
