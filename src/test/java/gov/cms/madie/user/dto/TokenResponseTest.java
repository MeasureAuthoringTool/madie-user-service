package gov.cms.madie.user.dto;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
  void returnsNullExpiresAtForNullAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken(null).build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for null accessToken", expiresAt, is(nullValue()));
  }

  @Test
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
  void isExpiredReturnsTrueForTokenExpiringSoonNearEdge() {
    // given
    long soonEpoch = Instant.now().plusSeconds(29).getEpochSecond();
    String jwt = createJwtWithExp(soonEpoch);
    TokenResponse token = TokenResponse.builder().accessToken(jwt).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired if expiring within 30 seconds", expired, is(true));
  }

  @Test
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
  void isExpiredReturnsTrueForNullAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken(null).build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired for null accessToken", expired, is(true));
  }

  @Test
  void isExpiredReturnsTrueForMalformedJwt() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("bad.token").build();
    // when
    boolean expired = token.isExpired();
    // then
    assertThat("Token should be expired for malformed JWT", expired, is(true));
  }

  @Test
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
  void returnsCachedExpiresAtIfAlreadySet() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("irrelevant").build();
    Instant cached = Instant.now().plusSeconds(1000);
    ReflectionTestUtils.setField(token, "expiresAt", cached);
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("should return cached expiresAt", expiresAt, is(cached));
  }

  @Test
  void returnsNullExpiresAtForEmptyAccessToken() {
    // given
    TokenResponse token = TokenResponse.builder().accessToken("").build();
    // when
    Instant expiresAt = token.getExpiresAt();
    // then
    assertThat("expiresAt should be null for empty accessToken", expiresAt, is(nullValue()));
  }
}
