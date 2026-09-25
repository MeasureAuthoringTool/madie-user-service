package gov.cms.madie.user.services;

import gov.cms.madie.user.config.MeasureServiceConfig;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.UserMeasuresDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeasureServiceClientTest {

  @Mock private MeasureServiceConfig measureServiceConfig;
  @Mock private RestTemplate measureServiceRestTemplate;

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<List<String>>> entityCaptor;

  private MeasureServiceClient client;

  @BeforeEach
  void setUp() {
    client = new MeasureServiceClient(measureServiceConfig, measureServiceRestTemplate);
  }

  private MeasureDTO measure(String name) {
    return MeasureDTO.builder().measureName(name).build();
  }

  private UserMeasuresDto userMeasures(String owned, String shared) {
    return new UserMeasuresDto(List.of(measure(owned)), List.of(measure(shared)));
  }

  @Test
  void getMeasuresForUsersPostsToBulkEndpointForwardsAuthAndReturnsBody() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    Map<String, UserMeasuresDto> responseBody =
        Map.of(
            "harp1", userMeasures("A", "B"),
            "harp2", userMeasures("C", "D"));
    doReturn(ResponseEntity.ok(responseBody))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    List<String> harpIds = List.of("harp1", "harp2");
    Map<String, UserMeasuresDto> result = client.getMeasuresForUsers(harpIds, "Bearer tok");

    assertThat(result, is(responseBody));
    assertThat(result.get("harp1").getOwnedMeasures().get(0).getMeasureName(), is("A"));
    assertThat(result.get("harp1").getSharedMeasures().get(0).getMeasureName(), is("B"));

    verify(measureServiceRestTemplate, times(1))
        .exchange(
            urlCaptor.capture(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));

    assertThat(urlCaptor.getValue(), is("http://measure:8080/api/admin/measures/bulk-fetch-for-users"));

    HttpEntity<List<String>> sentEntity = entityCaptor.getValue();
    assertThat(sentEntity.getBody(), is(harpIds));
    assertThat(sentEntity.getHeaders().getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(sentEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is("Bearer tok"));
  }

  @Test
  void getMeasuresForUsersOmitsAuthHeaderWhenBlank() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(ResponseEntity.ok(Map.of()))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    client.getMeasuresForUsers(List.of("harp1"), "   ");

    verify(measureServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is(nullValue()));
    assertThat(
        entityCaptor.getValue().getHeaders().getContentType(), is(MediaType.APPLICATION_JSON));
  }

  @Test
  void getMeasuresForUsersSendsNullBodyWhenHarpIdsNull() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(ResponseEntity.ok(Map.of()))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    client.getMeasuresForUsers(null, null);

    verify(measureServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));
    assertThat(entityCaptor.getValue().getBody(), is(nullValue()));
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is(nullValue()));
  }

  @Test
  void getMeasuresForUsersReturnsEmptyMapWhenBodyNull() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(ResponseEntity.ok(null))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    Map<String, UserMeasuresDto> result =
        client.getMeasuresForUsers(List.of("harp1"), "Bearer tok");

    assertThat(result, is(anEmptyMap()));
    verify(measureServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }
}
