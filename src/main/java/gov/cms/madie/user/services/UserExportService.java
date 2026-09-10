package gov.cms.madie.user.services;

import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.UserExportRequest;
import gov.cms.madie.user.dto.UserExportRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

/**
 * Facilitates the Full User Export: aggregates user data into export rows and delegates .xlsx
 * generation to the downstream excel-export service.
 */
@Slf4j
@Service
public class UserExportService {

  /** Media type for the Office Open XML spreadsheet (.xlsx). */
  public static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static final String USER_EXPORT_PATH = "/excel/user-export";

  private final ExcelExportServiceConfig excelExportServiceConfig;
  private final RestTemplate excelExportRestTemplate;

  public UserExportService(
      ExcelExportServiceConfig excelExportServiceConfig,
      @Qualifier("excelExportRestTemplate") RestTemplate excelExportRestTemplate) {
    this.excelExportServiceConfig = excelExportServiceConfig;
    this.excelExportRestTemplate = excelExportRestTemplate;
  }

  /**
   * Generates the Full User Export workbook by aggregating user data and calling the excel-export
   * service.
   *
   * @param authorizationHeader the caller's {@code Authorization} header to forward downstream (may
   *     be {@code null})
   * @return the generated .xlsx as bytes
   */
  public byte[] generateUserExport(String authorizationHeader) {
    List<UserExportRow> rows = buildRows();
    UserExportRequest requestBody = UserExportRequest.builder().rows(rows).build();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.parseMediaType(XLSX_MEDIA_TYPE)));
    // Forward the caller's auth downstream if the excel-export service requires it.
    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }

    String url = excelExportServiceConfig.getBaseUrl() + USER_EXPORT_PATH;
    HttpEntity<UserExportRequest> requestEntity = new HttpEntity<>(requestBody, headers);

    log.info("Requesting Full User Export from excel-export service for {} row(s)", rows.size());
    try {
      ResponseEntity<byte[]> response =
          excelExportRestTemplate.exchange(url, HttpMethod.POST, requestEntity, byte[].class);
      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        log.error(
            "excel-export service returned a non-2xx/empty response: status={}",
            response.getStatusCode());
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Failed to generate the user export: excel-export service returned "
                + response.getStatusCode());
      }
      return response.getBody();
    } catch (RestClientException ex) {
      log.error("Error calling excel-export service to generate the user export", ex);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Failed to generate the user export: unable to reach excel-export service",
          ex);
    }
  }

  /**
   * Aggregation seam for the export rows.
   *
   * <p>Returns an empty list for now (data population is a later story). Future work will build the
   * rows from users + owned/shared measures + owned/shared libraries here.
   *
   * @return the export rows (currently always empty)
   */
  public List<UserExportRow> buildRows() {
    return Collections.emptyList();
  }
}
