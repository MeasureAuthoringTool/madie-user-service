package gov.cms.madie.user.config;

import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.user.config.security.SecurityExceptionHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SecurityExceptionHandlersTest {

  private SecurityExceptionHandlers handlers;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    handlers = new SecurityExceptionHandlers();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @Test
  void handleAccessDeniedReturns403WithJsonBody() throws Exception {
    request.setRequestURI("/admin/users/refresh");

    handlers.handle(request, response, new AccessDeniedException("Access is denied"));

    assertThat(response.getStatus(), is(403));
    assertThat(response.getContentType(), is("application/json"));

    Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
    assertThat(body.get("status"), is(403));
    assertThat(body.get("error"), is("Forbidden"));
    assertThat(
        (String) body.get("message"),
        is("User does not have the required role to access this resource"));
    assertThat(body.get("path"), is("/admin/users/refresh"));
    assertThat(body, hasKey("timestamp"));
  }

  @Test
  void commenceReturns401WithJsonBody() throws Exception {
    request.setRequestURI("/users/123");

    handlers.commence(request, response, new BadCredentialsException("Bad credentials"));

    assertThat(response.getStatus(), is(401));
    assertThat(response.getContentType(), is("application/json"));

    Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
    assertThat(body.get("status"), is(401));
    assertThat(body.get("error"), is("Unauthorized"));
    assertThat(
        (String) body.get("message"), is("Authentication is required to access this resource"));
    assertThat(body.get("path"), is("/users/123"));
    assertThat(body, hasKey("timestamp"));
  }
}
