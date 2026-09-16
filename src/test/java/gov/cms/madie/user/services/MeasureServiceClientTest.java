package gov.cms.madie.user.services;

import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.user.config.MeasureServiceConfig;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.PageResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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
  @Captor private ArgumentCaptor<HttpEntity<Void>> entityCaptor;

  private MeasureServiceClient client;

  @BeforeEach
  void setUp() {
    client = new MeasureServiceClient(measureServiceConfig, measureServiceRestTemplate);
  }

  private MeasureDTO measure(String name) {
    return MeasureDTO.builder().measureName(name).build();
  }

  private PageResponse<MeasureDTO> page(List<MeasureDTO> content, boolean last) {
    PageResponse<MeasureDTO> page = new PageResponse<>();
    page.setContent(content);
    page.setLast(last);
    return page;
  }

  @Test
  void getMeasuresForUserFollowsPaginationAndForwardsAuth() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(
            ResponseEntity.ok(page(List.of(measure("A")), false)),
            ResponseEntity.ok(page(List.of(measure("B")), true)))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    List<MeasureDTO> result = client.getMeasuresForUser("harp1", OwnershipType.OWNED, "Bearer tok");

    assertThat(result, hasSize(2));
    assertThat(result.get(0).getMeasureName(), is("A"));
    assertThat(result.get(1).getMeasureName(), is("B"));

    verify(measureServiceRestTemplate, times(2))
        .exchange(
            urlCaptor.capture(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));

    List<String> urls = urlCaptor.getAllValues();
    assertThat(urls.get(0), containsString("/admin/userProfile/harp1/measures/searches"));
    assertThat(urls.get(0), containsString("ownershipTypes=OWNED"));
    assertThat(urls.get(0), containsString("limit=100"));
    assertThat(urls.get(0), containsString("page=0"));
    assertThat(urls.get(1), containsString("page=1"));
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is("Bearer tok"));
  }

  @Test
  void getMeasuresForUserUsesSharedOwnershipAndOmitsBlankAuth() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(ResponseEntity.ok(page(List.of(), true)))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    List<MeasureDTO> result = client.getMeasuresForUser("harp1", OwnershipType.SHARED, null);

    assertThat(result, is(empty()));
    verify(measureServiceRestTemplate, times(1))
        .exchange(
            urlCaptor.capture(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));
    assertThat(urlCaptor.getValue(), containsString("ownershipTypes=SHARED"));
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is(nullValue()));
  }

  @Test
  void getMeasuresForUserReturnsEmptyWhenBodyHasNoContent() {
    when(measureServiceConfig.getBaseUrl()).thenReturn("http://measure:8080/api");
    doReturn(ResponseEntity.ok(new PageResponse<MeasureDTO>()))
        .when(measureServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    List<MeasureDTO> result = client.getMeasuresForUser("harp1", OwnershipType.OWNED, "Bearer tok");

    assertThat(result, is(empty()));
    verify(measureServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }
}
