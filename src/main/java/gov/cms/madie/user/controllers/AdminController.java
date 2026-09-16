package gov.cms.madie.user.controllers;

import gov.cms.madie.user.config.security.AdminOnly;
import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import gov.cms.madie.user.services.UserExportService;
import gov.cms.madie.user.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@AdminOnly
public class AdminController {

  private static final DateTimeFormatter FILENAME_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final UpdateUserJobScheduler updateUserJobScheduler;
  private final UserService userService;
  private final UserExportService userExportService;

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

  @GetMapping(value = "/users/export", produces = UserExportService.XLSX_MEDIA_TYPE)
  public ResponseEntity<byte[]> exportUsers(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      Principal principal) {
    log.info(
        "User [{}] - Generating Full User Export",
        principal != null ? principal.getName() : "unknown");

    byte[] workbook = userExportService.generateUserExport(authorization);

    String filename = "UserExport_" + LocalDateTime.now().format(FILENAME_TIMESTAMP) + ".xlsx";

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(UserExportService.XLSX_MEDIA_TYPE))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(workbook);
  }
}
