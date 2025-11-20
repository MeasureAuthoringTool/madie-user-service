package gov.cms.madie.user.services;

import gov.cms.madie.user.dto.TokenResponse;
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
  void getCurrentTokenRefreshesTokenWhenTokenIsNull() throws Exception {
    // given
    var field = TokenManager.class.getDeclaredField("currentToken");
    field.setAccessible(true);
    field.set(null, null);
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
  void constructorCallsForceRefreshToken() {
    // given/when
    // setUp already calls constructor
    // then
    verify(harpProxyService, times(1)).getToken();
  }
}
