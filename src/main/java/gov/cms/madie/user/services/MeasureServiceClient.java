package gov.cms.madie.user.services;

import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.user.config.MeasureServiceConfig;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.PageResponse;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the downstream measure-service admin API. Retrieves a user's owned or shared measures
 * via {@code PUT /admin/userProfile/{harpId}/measures/searches}, following pagination, or - for the
 * Full User Export - many users at once via {@code PUT /admin/measures/bulk-export}.
 */
@Slf4j
@Component
public class MeasureServiceClient {

  private static final String USER_MEASURES_PATH = "/admin/userProfile/{harpId}/measures/searches";
  private static final String BULK_EXPORT_PATH = "/admin/measures/bulk-export";
  private static final int PAGE_SIZE = 100;

  private final MeasureServiceConfig measureServiceConfig;
  private final RestTemplate measureServiceRestTemplate;

  public MeasureServiceClient(
      MeasureServiceConfig measureServiceConfig,
      @Qualifier("measureServiceRestTemplate") RestTemplate measureServiceRestTemplate) {
    this.measureServiceConfig = measureServiceConfig;
    this.measureServiceRestTemplate = measureServiceRestTemplate;
  }

  /**
   * Retrieves all measures of the given ownership type for a user, following pagination.
   *
   * @param harpId the user's HARP ID
   * @param ownershipType {@code OWNED} or {@code SHARED}
   * @param authorizationHeader the admin caller's Authorization header to forward (may be null)
   * @return all matching measures (never null)
   */
  public List<MeasureDTO> getMeasuresForUser(
      String harpId, OwnershipType ownershipType, String authorizationHeader) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

    List<MeasureDTO> measures = new ArrayList<>();
    int page = 0;
    while (true) {
      String url =
          UriComponentsBuilder.fromUriString(measureServiceConfig.getBaseUrl())
              .path(USER_MEASURES_PATH)
              .queryParam("ownershipTypes", ownershipType.name())
              .queryParam("limit", PAGE_SIZE)
              .queryParam("page", page)
              .buildAndExpand(harpId)
              .toUriString();

      ResponseEntity<PageResponse<MeasureDTO>> response =
          measureServiceRestTemplate.exchange(
              url,
              HttpMethod.PUT,
              requestEntity,
              new ParameterizedTypeReference<PageResponse<MeasureDTO>>() {});

      PageResponse<MeasureDTO> body = response.getBody();
      if (body == null || body.getContent() == null || body.getContent().isEmpty()) {
        break;
      }
      measures.addAll(body.getContent());
      if (body.isLast()) {
        break;
      }
      page++;
    }
    return measures;
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
