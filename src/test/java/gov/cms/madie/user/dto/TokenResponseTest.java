package gov.cms.madie.user.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TokenResponseTest {

  // Helper to create a JWT with a given exp value, matching expected payload format
  private String createJwtWithExp(long expSeconds) {
    String header = Base64.getUrlEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    String payload =
        Base64.getUrlEncoder()
            .encodeToString(("{\"exp\":" + expSeconds + ",\"other\":1}").getBytes());
    String signature =
        Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
    return header + "." + payload + "." + signature;
  }

  @Test
  @DisplayName("should return correct Instant for valid JWT")
  void returnsCorrectExpiresAtForValidJwt() {
    // given
    long futureEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
    String jwt = createJwtWithExp(futureEpoch);
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should match JWT exp", expiresAt.getEpochSecond(), is(futureEpoch));
  }

  @Test
  @DisplayName("should return null for invalid JWT")
  void returnsNullExpiresAtForInvalidJwt() {
    // given
    String jwt = "invalid.jwt.token";
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for invalid JWT", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should return null for null accessToken")
  void returnsNullExpiresAtForNullAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken(null).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for null accessToken", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should be expired for token with past exp")
  void isExpiredReturnsTrueForExpiredToken() {
    // given
    long pastEpoch = Instant.now().minusSeconds(3600).getEpochSecond();
    String jwt = createJwtWithExp(pastEpoch);
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired", expired, is(true));
  }

  @Test
  @DisplayName("should be expired for token expiring within 30 seconds")
  void isExpiredReturnsTrueForTokenExpiringSoon() {
    // given
    long soonEpoch = Instant.now().plusSeconds(10).getEpochSecond();
    String jwt = createJwtWithExp(soonEpoch);
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired if expiring within 30 seconds", expired, is(true));
  }

  @Test
  @DisplayName("should not be expired for token expiring after 30 seconds")
  void isExpiredReturnsFalseForTokenExpiringLater() {
    // given
    long futureEpoch = Instant.now().plusSeconds(120).getEpochSecond();
    String jwt = createJwtWithExp(futureEpoch);
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should not be expired if expiring after 30 seconds", expired, is(false));
  }

  @Test
  @DisplayName("should be expired for null accessToken")
  void isExpiredReturnsTrueForNullAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken(null).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired for null accessToken", expired, is(true));
  }

  @Test
  @DisplayName("should be expired for malformed JWT")
  void isExpiredReturnsTrueForMalformedJwt() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("bad.token").build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired for malformed JWT", expired, is(true));
  }

  @Test
  @DisplayName("should return null if JWT does not have three parts")
  void returnsNullExpiresAtForJwtWithWrongParts() {
    // given
    String jwt = "only.one.part";
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for JWT with wrong parts", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should return null if JWT payload does not contain exp field")
  void returnsNullExpiresAtForJwtWithoutExpField() {
    // given
    String header = Base64.getUrlEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    String payload = Base64.getUrlEncoder().encodeToString("{\"other\":1}".getBytes());
    String signature =
        Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
    String jwt = header + "." + payload + "." + signature;
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null if exp field is missing", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should return null if exp field is not a valid number")
  void returnsNullExpiresAtForJwtWithInvalidExpField() {
    // given
    String header = Base64.getUrlEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    String payload =
        Base64.getUrlEncoder().encodeToString("{\"exp\":\"notanumber\",\"other\":1}".getBytes());
    String signature =
        Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
    String jwt = header + "." + payload + "." + signature;
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat(
        "expiresAt should be null if exp field is not a valid number", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should return null if exp field substring logic fails")
  void returnsNullExpiresAtForJwtWithMalformedExpField() {
    // given
    String header = Base64.getUrlEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    // No comma or closing brace after exp
    String payload = Base64.getUrlEncoder().encodeToString("{\"exp\":1234567890".getBytes());
    String signature =
        Base64.getUrlEncoder().withoutPadding().encodeToString("dummy-signature".getBytes());
    String jwt = header + "." + payload + "." + signature;
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat(
        "expiresAt should be null if exp field substring logic fails", expiresAt, is(nullValue()));
  }

  @Test
  @DisplayName("should return cached expiresAt if already set")
  void returnsCachedExpiresAtIfAlreadySet() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("irrelevant").build();
    Instant cached = Instant.now().plusSeconds(1000);
    // Use reflection to set private field
    try {
      java.lang.reflect.Field field = TokenResponse.class.getDeclaredField("expiresAt");
      field.setAccessible(true);
      field.set(token, cached);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("should return cached expiresAt", expiresAt, is(cached));
  }

  @Test
  @DisplayName("should return null for empty accessToken string")
  void returnsNullExpiresAtForEmptyAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("").build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for empty accessToken", expiresAt, is(nullValue()));
  }
}
