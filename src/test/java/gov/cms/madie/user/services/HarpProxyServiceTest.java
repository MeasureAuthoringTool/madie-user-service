package gov.cms.madie.user.services;

import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.HarpResponseWrapper;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserDetailsResponse;
import gov.cms.madie.user.dto.UserRolesResponse;
import gov.cms.madie.user.dto.HarpErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarpProxyServiceTest {

  @Mock private HarpConfig harpConfig;
  @Mock private RestTemplate harpRestTemplate;
  @InjectMocks private HarpProxyService harpProxyService;

  @BeforeEach
  void setUp() {
    HarpConfig.Token tokenConfig = new HarpConfig.Token();
    tokenConfig.setClientId("clientId");
    tokenConfig.setSecret("secret");
    tokenConfig.setScope("scope");
    tokenConfig.setUri("/token");
  }

  @Test
  void getTokenReturnsTokenResponse() {
    // given
    TokenResponse expected = TokenResponse.builder().accessToken("token").build();
    when(harpConfig.getToken())
        .thenReturn(
            HarpConfig.Token.builder()
                .clientId("clientId")
                .secret("secret")
                .scope("scope")
                .uri("/token")
                .build());
    ResponseEntity<TokenResponse> responseEntity = ResponseEntity.ok(expected);
    when(harpRestTemplate.exchange(anyString(), any(), any(), eq(TokenResponse.class)))
        .thenReturn(responseEntity);
    // when
    TokenResponse actual = harpProxyService.getToken();
    // then
    assertThat(actual.getAccessToken(), is("token"));
  }

  @Test
  void fetchUserDetailsReturnsUserDetailsResponse() {
    // given
    List<String> harpIds = List.of("id1", "id2");
    String token = "accessToken";
    when(harpConfig.getUserFind())
        .thenReturn(HarpConfig.UserFind.builder().uri("/userFindApi").build());
    UserDetailsResponse expected = new UserDetailsResponse();
    when(harpRestTemplate.postForObject(anyString(), any(), eq(UserDetailsResponse.class)))
        .thenReturn(expected);
    // when
    UserDetailsResponse actual = harpProxyService.fetchUserDetails(harpIds, token);
    // then
    assertThat(actual, is(expected));
  }

  @Test
  void fetchUserRolesReturnsUserRolesResponse() {
    // given
    String harpId = "id1";
    String token = "accessToken";
    when(harpConfig.getUserRoles())
        .thenReturn(HarpConfig.UserRoles.builder().uri("/userRoleCreationApi").build());
    UserRolesResponse userRolesResponse = UserRolesResponse.builder().success(true).build();
    HarpResponseWrapper<UserRolesResponse> expected =
        HarpResponseWrapper.<UserRolesResponse>builder()
            .response(userRolesResponse)
            .statusCode(HttpStatus.OK)
            .build();
    when(harpRestTemplate.postForEntity(anyString(), any(), eq(UserRolesResponse.class)))
        .thenReturn(ResponseEntity.ok(userRolesResponse));
    // when
    HarpResponseWrapper<UserRolesResponse> actual = harpProxyService.fetchUserRoles(harpId, token);
    // then
    assertThat(actual, is(expected));
  }

  @Test
  void fetchUserRolesHandlesRoleCreation027Error() throws Exception {
    String harpId = "id1";
    String token = "accessToken";
    when(harpConfig.getUserRoles())
        .thenReturn(HarpConfig.UserRoles.builder().uri("/userRoleCreationApi").build());
    HarpErrorResponse errorResponse =
        HarpErrorResponse.builder()
            .errorCode("ERR-ROLECREATION-027")
            .errorSummary("User not found")
            .details("user id1 not found")
            .build();
    String errorJson =
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResponse);
    HttpStatusCodeException ex =
        new HttpStatusCodeException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            new HttpHeaders(),
            errorJson.getBytes(),
            null) {};
    when(harpRestTemplate.postForEntity(anyString(), any(), eq(UserRolesResponse.class)))
        .thenThrow(ex);
    HarpProxyService service =
        new HarpProxyService(
            harpConfig, harpRestTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
    HarpResponseWrapper<UserRolesResponse> result = service.fetchUserRoles(harpId, token);
    assertThat(result.getError().getErrorCode(), is("ERR-ROLECREATION-027"));
    assertThat(result.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThat(result.getResponse(), is(nullValue()));
  }

  @Test
  void fetchUserRolesHandlesOtherErrorCode() throws Exception {
    String harpId = "id2";
    String token = "accessToken";
    when(harpConfig.getUserRoles())
        .thenReturn(HarpConfig.UserRoles.builder().uri("/userRoleCreationApi").build());
    HarpErrorResponse errorResponse =
        HarpErrorResponse.builder()
            .errorCode("ERR-OTHER")
            .errorSummary("Some other error")
            .details("unexpected error")
            .build();
    String errorJson =
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResponse);
    HttpStatusCodeException ex =
        new HttpStatusCodeException(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            new HttpHeaders(),
            errorJson.getBytes(),
            null) {};
    when(harpRestTemplate.postForEntity(anyString(), any(), eq(UserRolesResponse.class)))
        .thenThrow(ex);
    HarpProxyService service =
        new HarpProxyService(
            harpConfig, harpRestTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
    HarpResponseWrapper<UserRolesResponse> result = service.fetchUserRoles(harpId, token);
    assertThat(result.getError().getErrorCode(), is("ERR-OTHER"));
    assertThat(result.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat(result.getResponse(), is(nullValue()));
  }

  @Test
  void fetchUserRolesHandlesUnparseableErrorResponse() {
    String harpId = "id3";
    String token = "accessToken";
    when(harpConfig.getUserRoles())
        .thenReturn(HarpConfig.UserRoles.builder().uri("/userRoleCreationApi").build());
    String errorJson = "not a json";
    HttpStatusCodeException ex =
        new HttpStatusCodeException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            new HttpHeaders(),
            errorJson.getBytes(),
            null) {};
    when(harpRestTemplate.postForEntity(anyString(), any(), eq(UserRolesResponse.class)))
        .thenThrow(ex);
    HarpProxyService service =
        new HarpProxyService(
            harpConfig, harpRestTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
    HarpResponseWrapper<UserRolesResponse> result = service.fetchUserRoles(harpId, token);
    assertThat(result.getError(), is(nullValue()));
    assertThat(result.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThat(result.getException(), is(ex));
    assertThat(result.getResponse(), is(nullValue()));
  }
}
