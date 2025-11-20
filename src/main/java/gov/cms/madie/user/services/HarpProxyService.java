package gov.cms.madie.user.services;

import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.TokenRequest;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserDetailsRequest;
import gov.cms.madie.user.dto.UserDetailsResponse;
import gov.cms.madie.user.dto.UserRolesRequest;
import gov.cms.madie.user.dto.UserRolesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HarpProxyService {

  private final HarpConfig harpConfig;
  private final RestTemplate harpRestTemplate;

  /**
   * Fetches an access token from the HARP API using clientId and secret from environment.
   *
   * @return TokenResponse containing the access token and related information.
   */
  @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 100))
  public TokenResponse getToken() {
    // Create Basic Auth header
    String auth = harpConfig.getToken().getClientId() + ":" + harpConfig.getToken().getSecret();
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Basic " + encodedAuth);
    headers.set("Content-Type", "application/json");

    // Create request body with scope
    TokenRequest tokenRequest =
        TokenRequest.builder().scope(harpConfig.getToken().getScope()).build();

    HttpEntity<TokenRequest> requestEntity = new HttpEntity<>(tokenRequest, headers);

    // Make POST call to token endpoint
    return harpRestTemplate
        .exchange(
            harpConfig.getToken().getUri(), HttpMethod.POST, requestEntity, TokenResponse.class)
        .getBody();
  }

  /**
   * Fetches user details from the HARP API for a list of HARP IDs using the provided access token.
   *
   * @param harpIds List of HARP IDs to fetch details for.
   * @param harpToken Access token to authenticate the request.
   * @return UserDetailsResponse containing the user details for the provided HARP IDs.
   */
  public UserDetailsResponse fetchUserDetails(List<String> harpIds, String harpToken) {
    String url = harpConfig.getBaseUrl() + harpConfig.getUserFind().getUri() + "/findUser";
    HttpHeaders headers = createApiHeaders(harpToken);

    UserDetailsRequest body =
        UserDetailsRequest.builder()
            .programName(harpConfig.getProgramName())
            .attributes(java.util.Map.of("username", harpIds))
            .details("all")
            .offset(0)
            .max(harpIds.size())
            .build();

    HttpEntity<UserDetailsRequest> requestEntity = new HttpEntity<>(body, headers);
    return harpRestTemplate.postForObject(url, requestEntity, UserDetailsResponse.class);
  }

  /**
   * Fetches user roles from the HARP API for a given HARP ID using the provided access token.
   *
   * @param harpId HARP ID of the user to fetch roles for.
   * @param harpToken Access token to authenticate the request.
   * @return UserRolesResponse containing the user roles for the provided HARP ID.
   */
  public UserRolesResponse fetchUserRoles(String harpId, String harpToken) {
    String url = harpConfig.getBaseUrl() + harpConfig.getUserRoles().getUri() + "/getUserRoles";
    HttpHeaders headers = createApiHeaders(harpToken);

    UserRolesRequest body =
        UserRolesRequest.builder()
            .userName(harpId)
            .adoName(harpConfig.getAdoName())
            .programName(harpConfig.getProgramName())
            .build();

    HttpEntity<UserRolesRequest> requestEntity = new HttpEntity<>(body, headers);

    // Make POST call
    return harpRestTemplate.postForObject(url, requestEntity, UserRolesResponse.class);
  }

  private HttpHeaders createApiHeaders(String harpToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + harpToken);
    headers.set("Content-Type", "application/json");
    return headers;
  }
}
