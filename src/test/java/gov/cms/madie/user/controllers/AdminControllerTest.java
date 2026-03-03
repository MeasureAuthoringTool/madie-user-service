package gov.cms.madie.user.controllers;

import gov.cms.madie.user.dto.UserLoginDto;
import gov.cms.madie.user.services.UpdateUserJobScheduler;
import gov.cms.madie.user.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

  @Mock private UpdateUserJobScheduler updateUserJobScheduler;
  @Mock private UserService userService;
  @Mock private Principal principal;

  @InjectMocks private AdminController adminController;

  private HttpServletRequest request;
  private String apiKey;

  @BeforeEach
  void setUp() {
    apiKey = "test-api-key";

    // Create MockHttpServletRequest and set the api-key header
    MockHttpServletRequest mockRequest = new MockHttpServletRequest();
    mockRequest.addHeader("api-key", apiKey);
    request = mockRequest;

    when(principal.getName()).thenReturn("testUser");
  }

  @Test
  void refreshAllUsers() {
    // when
    ResponseEntity<Object> response =
        adminController.refreshAllUsers(request, apiKey, principal, null);

    // then
    assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody(), is("User refresh job accepted"));
  }

  @Test
  void getLastLoginReturnsAllUsers() {
    List<UserLoginDto> users =
        List.of(
            UserLoginDto.builder().harpId("user1").lastLoginAt(Instant.now()).build(),
            UserLoginDto.builder().harpId("user2").lastLoginAt(Instant.now()).build());
    when(userService.getAllMadieUsers()).thenReturn(users);

    ResponseEntity<Object> response = adminController.getLastLogin(request, apiKey, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody(), is(users));
  }

  @Test
  void getLastLoginReturnsEmptyListWhenNoUsers() {
    when(userService.getAllMadieUsers()).thenReturn(Collections.emptyList());

    ResponseEntity<Object> response = adminController.getLastLogin(request, apiKey, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody(), is(Collections.emptyList()));
  }
}
