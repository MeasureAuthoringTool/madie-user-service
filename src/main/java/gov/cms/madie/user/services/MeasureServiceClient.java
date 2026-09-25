package gov.cms.madie.user.services;

import gov.cms.madie.user.config.MeasureServiceConfig;
import gov.cms.madie.user.dto.UserMeasuresDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MeasureServiceClient {

  private static final String BULK_EXPORT_PATH = "/admin/measures/bulk-export";

  private final MeasureServiceConfig measureServiceConfig;
  private final RestTemplate measureServiceRestTemplate;

  public MeasureServiceClient(
      MeasureServiceConfig measureServiceConfig,
      @Qualifier("measureServiceRestTemplate") RestTemplate measureServiceRestTemplate) {
    this.measureServiceConfig = measureServiceConfig;
    this.measureServiceRestTemplate = measureServiceRestTemplate;
  }

  /**
   * Retrieves the owned and shared measures (latest per family) for many users in a single request,
   * used by the Full User Export to avoid two search calls per user.
   *
   * @param harpIds the users to fetch; when null/empty, measure-service returns all users
   * @param authorizationHeader the admin caller's Authorization header to forward (may be null)
   * @return map of lower-cased HARP id -&gt; owned/shared measures (never null)
   */
  public Map<String, UserMeasuresDto> getMeasuresForUsers(
      List<String> harpIds, String authorizationHeader) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    HttpEntity<List<String>> requestEntity = new HttpEntity<>(harpIds, headers);

    String url =
        UriComponentsBuilder.fromUriString(measureServiceConfig.getBaseUrl())
            .path(BULK_EXPORT_PATH)
            .toUriString();

    ResponseEntity<Map<String, UserMeasuresDto>> response =
        measureServiceRestTemplate.exchange(
            url,
            HttpMethod.PUT,
            requestEntity,
            new ParameterizedTypeReference<Map<String, UserMeasuresDto>>() {});

    Map<String, UserMeasuresDto> body = response.getBody();
    return body == null ? Collections.emptyMap() : body;
  }
}
