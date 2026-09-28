package gov.cms.madie.user.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.user.config.CqlLibraryServiceConfig;
import gov.cms.madie.user.dto.LibraryDTO;
import gov.cms.madie.user.dto.UserLibrariesDto;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceClientTest {

  @Mock private CqlLibraryServiceConfig cqlLibraryServiceConfig;
  @Mock private RestTemplate cqlLibraryServiceRestTemplate;

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<List<String>>> entityCaptor;

  private CqlLibraryServiceClient client;

  @BeforeEach
  void setUp() {
    client = new CqlLibraryServiceClient(cqlLibraryServiceConfig, cqlLibraryServiceRestTemplate);
  }

  private LibraryDTO library(String name) {
    return new LibraryDTO(name, "1.0.000", null, "QI-Core v4.1.1", null, true, null);
  }

  private UserLibrariesDto userLibraries(String owned, String shared) {
    return new UserLibrariesDto(List.of(library(owned)), List.of(library(shared)));
  }

  @Test
  void getLibrariesForUsersPutsToBulkEndpointForwardsAuthAndReturnsBody() {
    when(cqlLibraryServiceConfig.getBaseUrl()).thenReturn("http://cql-library:8082/api");
    Map<String, UserLibrariesDto> responseBody =
        Map.of(
            "harp1", userLibraries("A", "B"),
            "harp2", userLibraries("C", "D"));
    doReturn(ResponseEntity.ok(responseBody))
        .when(cqlLibraryServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    List<String> harpIds = List.of("harp1", "harp2");
    Map<String, UserLibrariesDto> result = client.getLibrariesForUsers(harpIds, "Bearer tok");

    assertThat(result, is(responseBody));
    assertThat(result.get("harp1").getOwnedLibraries().get(0).getCqlLibraryName(), is("A"));
    assertThat(result.get("harp1").getSharedLibraries().get(0).getCqlLibraryName(), is("B"));

    verify(cqlLibraryServiceRestTemplate, times(1))
        .exchange(
            urlCaptor.capture(),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            any(ParameterizedTypeReference.class));

    assertThat(
        urlCaptor.getValue(),
        is("http://cql-library:8082/api/cql-libraries/admin/bulk-fetch-for-users"));

    HttpEntity<List<String>> sentEntity = entityCaptor.getValue();
    assertThat(sentEntity.getBody(), is(harpIds));
    assertThat(sentEntity.getHeaders().getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(sentEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION), is("Bearer tok"));
  }

  @Test
  void getLibrariesForUsersOmitsAuthHeaderWhenBlank() {
    when(cqlLibraryServiceConfig.getBaseUrl()).thenReturn("http://cql-library:8082/api");
    doReturn(ResponseEntity.ok(Map.of()))
        .when(cqlLibraryServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    client.getLibrariesForUsers(List.of("harp1"), "   ");

    verify(cqlLibraryServiceRestTemplate, times(1))
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
  void getLibrariesForUsersSendsNullBodyWhenHarpIdsNull() {
    when(cqlLibraryServiceConfig.getBaseUrl()).thenReturn("http://cql-library:8082/api");
    doReturn(ResponseEntity.ok(Map.of()))
        .when(cqlLibraryServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    client.getLibrariesForUsers(null, null);

    verify(cqlLibraryServiceRestTemplate, times(1))
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
  void getLibrariesForUsersReturnsEmptyMapWhenBodyNull() {
    when(cqlLibraryServiceConfig.getBaseUrl()).thenReturn("http://cql-library:8082/api");
    doReturn(ResponseEntity.ok(null))
        .when(cqlLibraryServiceRestTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));

    Map<String, UserLibrariesDto> result = client.getLibrariesForUsers(List.of("harp1"), "tok");

    assertThat(result, is(anEmptyMap()));
    verify(cqlLibraryServiceRestTemplate, times(1))
        .exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class));
  }
}
