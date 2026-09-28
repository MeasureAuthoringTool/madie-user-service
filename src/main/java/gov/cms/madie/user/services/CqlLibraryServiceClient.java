package gov.cms.madie.user.services;

import gov.cms.madie.user.config.CqlLibraryServiceConfig;
import gov.cms.madie.user.dto.UserLibrariesDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class CqlLibraryServiceClient {

  private static final String BULK_EXPORT_PATH = "/cql-libraries/admin/bulk-fetch-for-users";

  private final CqlLibraryServiceConfig cqlLibraryServiceConfig;
  private final RestTemplate cqlLibraryServiceRestTemplate;

  public CqlLibraryServiceClient(
      CqlLibraryServiceConfig cqlLibraryServiceConfig,
      @Qualifier("cqlLibraryServiceRestTemplate") RestTemplate cqlLibraryServiceRestTemplate) {
    this.cqlLibraryServiceConfig = cqlLibraryServiceConfig;
    this.cqlLibraryServiceRestTemplate = cqlLibraryServiceRestTemplate;
  }

  /**
   * Retrieves the owned and shared CQL libraries (latest per family) for many users in a single
   * request, used by the Full User Export.
   *
   * @param harpIds the users to fetch; when null/empty, cql-library-service returns all users
   * @param authorizationHeader the admin caller's Authorization header to forward (may be null)
   * @return map of lower-cased HARP id -> owned/shared libraries (never null)
   */
  public Map<String, UserLibrariesDto> getLibrariesForUsers(
      List<String> harpIds, String authorizationHeader) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    HttpEntity<List<String>> requestEntity = new HttpEntity<>(harpIds, headers);

    String url =
        UriComponentsBuilder.fromUriString(cqlLibraryServiceConfig.getBaseUrl())
            .path(BULK_EXPORT_PATH)
            .toUriString();

    ResponseEntity<Map<String, UserLibrariesDto>> response =
        cqlLibraryServiceRestTemplate.exchange(
            url,
            HttpMethod.PUT,
            requestEntity,
            new ParameterizedTypeReference<Map<String, UserLibrariesDto>>() {});

    Map<String, UserLibrariesDto> body = response.getBody();
    return body == null ? Collections.emptyMap() : body;
  }
}
