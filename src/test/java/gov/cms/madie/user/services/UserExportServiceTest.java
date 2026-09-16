package gov.cms.madie.user.services;

import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.UserExportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserExportServiceTest {

  @Mock private ExcelExportServiceConfig excelExportServiceConfig;
  @Mock private RestTemplate excelExportRestTemplate;

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<UserExportRequest>> entityCaptor;

  private UserExportService userExportService;

  @BeforeEach
  void setUp() {
    userExportService = new UserExportService(excelExportServiceConfig, excelExportRestTemplate);
  }

  @Test
  void buildRowsReturnsEmptyListForNow() {
    assertThat(userExportService.buildRows(), is(empty()));
  }

  @Test
  void generateUserExportPostsEmptyRowsAndReturnsBytes() {
    // given
    byte[] expectedBytes = "fake-xlsx-bytes".getBytes(StandardCharsets.UTF_8);
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    // when
    byte[] actual = userExportService.generateUserExport("Bearer test-token");

    // then - returns the received bytes
    assertThat(actual, is(expectedBytes));

    // POSTs to /excel/user-export
    assertThat(urlCaptor.getValue(), is("http://excel-export:3000/api/excel/user-export"));

    // body has an empty rows array
    HttpEntity<UserExportRequest> sentEntity = entityCaptor.getValue();
    assertThat(sentEntity.getBody(), notNullValue());
    assertThat(sentEntity.getBody().getRows(), is(empty()));

    // requests the .xlsx back and forwards the caller's auth
    HttpHeaders headers = sentEntity.getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(
        headers.getAccept(), hasItem(MediaType.parseMediaType(UserExportService.XLSX_MEDIA_TYPE)));
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION), is("Bearer test-token"));
  }

  @Test
  void generateUserExportOmitsAuthHeaderWhenBlank() {
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok("bytes".getBytes(StandardCharsets.UTF_8)));

    userExportService.generateUserExport(null);

    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), nullValue());
  }

  @Test
  void generateUserExportThrowsBadGatewayOnNonSuccessResponse() {
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(byte[].class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> userExportService.generateUserExport("Bearer token"));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void generateUserExportThrowsBadGatewayWhenDownstreamUnreachable() {
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(), eq(byte[].class)))
        .thenThrow(new RestClientException("connection refused"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> userExportService.generateUserExport("Bearer token"));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }
}
