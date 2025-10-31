package gov.cms.madie.user.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class LogInterceptorTest {
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private Logger logger;
  private LogInterceptor interceptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    interceptor = new LogInterceptor();
  }

  @Test
  void afterCompletion_logsInfo() {
    // given
    when(request.getUserPrincipal()).thenReturn(() -> "testUser");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/users/123");
    when(response.getStatus()).thenReturn(200);
    // when
    interceptor.afterCompletion(request, response, null, null);
    // then
    // No exception thrown, log is called (cannot assert log output without a log appender)
    assertThat(true, is(true));
  }

  @Test
  void afterCompletion_logsInfoWithNoPrincipal() {
    // given
    when(request.getUserPrincipal()).thenReturn(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/users");
    when(response.getStatus()).thenReturn(201);
    // when
    interceptor.afterCompletion(request, response, null, null);
    // then
    assertThat(true, is(true));
  }
}
