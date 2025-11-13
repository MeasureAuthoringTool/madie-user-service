package gov.cms.madie.user.services;

import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.TokenRequest;
import gov.cms.madie.user.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class HarpProxyService {

  private final HarpConfig harpConfig;
  private final RestTemplate harpRestTemplate;

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

  public void fetchUserDetails(String harpId, String harpToken) {
    // TODO: fill in call to HARP Proxy to fetch user details by HARP ID and return a UserDetailsDto
  }

  public void fetchUserRoles(String harpId, String harpToken) {
    // TODO: fill in call to HARP Proxy to fetch user roles by HARP ID and return a list of roles
  }
}
