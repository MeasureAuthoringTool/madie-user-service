package gov.cms.madie.user.services;

import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.test.utils.TestRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

class TokenManagerTest {

  private HarpProxyService harpProxyService;
  private TokenResponse tokenResponse;
  private TokenManager tokenManager;

  @BeforeEach
  void setUp() {
    harpProxyService = mock(HarpProxyService.class);
    tokenResponse = mock(TokenResponse.class);
    when(harpProxyService.getToken()).thenReturn(tokenResponse);
    // Reset static field before each test
    ReflectionTestUtils.setField(TokenManager.class, "currentToken", null);
    tokenManager = new TokenManager(harpProxyService);
  }

  @Test
  void getCurrentTokenReturnsCurrentTokenWhenTokenIsValid() {
    // given
    when(tokenResponse.isExpired()).thenReturn(false);
    // when
    TokenResponse result = tokenManager.getCurrentToken();
    // then
    assertThat(result, is(tokenResponse));
    verify(harpProxyService, times(1)).getToken(); // called in constructor
  }

  @Test
  void getCurrentTokenRefreshesTokenWhenTokenIsNull() {
    // given
    ReflectionTestUtils.setField(TokenManager.class, "currentToken", null);
    // when
    TokenResponse result = tokenManager.getCurrentToken();
    // then
    assertThat(result, is(tokenResponse));
    verify(harpProxyService, atLeastOnce()).getToken();
  }

  @Test
  void getCurrentTokenRefreshesTokenWhenTokenIsExpired() {
    // given
    when(tokenResponse.isExpired()).thenReturn(true);
    // when
    TokenResponse result = tokenManager.getCurrentToken();
    // then
    assertThat(result, is(tokenResponse));
    verify(harpProxyService, atLeast(2)).getToken(); // constructor + expired
  }

  @Test
  void getCurrentTokenGracefullyHandlesHarpErrorWhenTokenIsExpired() {
    // given
    when(tokenResponse.isExpired()).thenReturn(true);
    doThrow(new TestRuntimeException("HARP service error")).when(harpProxyService).getToken();
    // when
    TokenResponse result = tokenManager.getCurrentToken();
    // then
    assertThat(result, is(nullValue()));
    verify(harpProxyService, atLeast(2)).getToken(); // constructor + expired
  }

  @Test
  void forceRefreshTokenUpdatesCurrentTokenAndLogs() {
    // given
    when(tokenResponse.getExpiresAt()).thenReturn(java.time.Instant.parse("2025-12-31T23:59:59Z"));
    // when
    tokenManager.forceRefreshToken();
    // then
    TokenResponse result = tokenManager.getCurrentToken();
    assertThat(result, is(tokenResponse));
    verify(harpProxyService, atLeast(2)).getToken(); // constructor + forceRefresh
  }

  @Test
  void forceRefreshTokenGracefullyHandlesHarpError() {
    // given
    when(tokenResponse.getExpiresAt()).thenReturn(java.time.Instant.parse("2025-12-31T23:59:59Z"));
    doThrow(new TestRuntimeException("HARP service error")).when(harpProxyService).getToken();
    // when
    tokenManager.forceRefreshToken();
    // then
    TokenResponse result = tokenManager.getCurrentToken();
    assertThat(result, is(nullValue()));
    verify(harpProxyService, atLeast(2)).getToken(); // constructor + forceRefresh
  }

  @Test
  void constructorCallsForceRefreshToken() {
    // given/when
    // setUp already calls constructor
    // then
    verify(harpProxyService, times(1)).getToken();
  }
}
