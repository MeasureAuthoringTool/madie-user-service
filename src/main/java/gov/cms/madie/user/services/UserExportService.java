package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.MeasureDTO;
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
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

  /** Shown (in red, in the Owned Measure Name column) when a user's measures can't be fetched. */
  static final String MEASURE_FETCH_ERROR_MESSAGE = "Unable to retrieve this user";

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final ExcelExportServiceConfig excelExportServiceConfig;
  private final RestTemplate excelExportRestTemplate;
  private final UserService userService;
  private final MeasureServiceClient measureServiceClient;

  public UserExportService(
      ExcelExportServiceConfig excelExportServiceConfig,
      @Qualifier("excelExportRestTemplate") RestTemplate excelExportRestTemplate,
      UserService userService,
      MeasureServiceClient measureServiceClient) {
    this.excelExportServiceConfig = excelExportServiceConfig;
    this.excelExportRestTemplate = excelExportRestTemplate;
    this.userService = userService;
    this.measureServiceClient = measureServiceClient;
  }

  /**
   * Generates the Full User Export workbook by aggregating user data and calling the excel-export
   * service.
   *
   * @param authorizationHeader the caller's {@code Authorization} header to forward downstream (may
   *     be {@code null})
   * @param harpIds the users to export; when null/empty, all users are exported
   * @return the generated .xlsx as bytes
   */
  public byte[] generateUserExport(String authorizationHeader, List<String> harpIds) {
    List<UserExportRow> rows = buildRows(authorizationHeader, harpIds);
    UserExportRequest requestBody = UserExportRequest.builder().rows(rows).build();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.parseMediaType(XLSX_MEDIA_TYPE)));

    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }

    String url = excelExportServiceConfig.getBaseUrl() + USER_EXPORT_PATH;
    HttpEntity<UserExportRequest> requestEntity = new HttpEntity<>(requestBody, headers);

    log.info("Requesting Full User Export from excel-export service for {} row(s)", rows.size());
    try {
      ResponseEntity<byte[]> response =
          excelExportRestTemplate.exchange(url, HttpMethod.PUT, requestEntity, byte[].class);
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
   * <p>Produces one or more rows per MADiE user. User Metadata (columns 1-9) is written on the
   * user's first row only. A user's owned and shared measures (columns 10-22) are fetched from the
   * measure-service and fanned out so that row {@code i} carries the {@code i}-th owned and shared
   * measure; a user therefore contributes {@code max(ownedCount, sharedCount, 1)} rows. If the
   * measure lookup fails, a single row is emitted with an error marker (rendered in red in the
   * Owned Measure Name column). Library columns (23-33) are future work.
   *
   * @param authorizationHeader the admin caller's Authorization header, forwarded to
   *     measure-service
   * @param harpIds the users to export; when null/empty, all users are exported
   * @return the flattened export rows across all users
   */
  public List<UserExportRow> buildRows(String authorizationHeader, List<String> harpIds) {
    List<MadieUser> users =
        CollectionUtils.isEmpty(harpIds)
            ? userService.getAllUsers()
            : userService.getUsersByHarpIds(harpIds);
    if (CollectionUtils.isEmpty(users)) {
      return Collections.emptyList();
    }
    List<UserExportRow> rows = new ArrayList<>();
    for (MadieUser user : users) {
      rows.addAll(buildRowsForUser(user, authorizationHeader));
    }
    return rows;
  }

  private List<UserExportRow> buildRowsForUser(MadieUser user, String authorizationHeader) {
    List<MeasureDTO> ownedMeasures;
    List<MeasureDTO> sharedMeasures;
    try {
      ownedMeasures =
          measureServiceClient.getMeasuresForUser(
              user.getHarpId(), OwnershipType.OWNED, authorizationHeader);
      sharedMeasures =
          measureServiceClient.getMeasuresForUser(
              user.getHarpId(), OwnershipType.SHARED, authorizationHeader);
    } catch (Exception ex) {
      log.error("Unable to retrieve measures for user [{}]", user.getHarpId(), ex);
      UserExportRow.UserExportRowBuilder errorRow = UserExportRow.builder();
      applyUserMetadata(errorRow, user);
      errorRow.measureError(MEASURE_FETCH_ERROR_MESSAGE);
      return List.of(errorRow.build());
    }

    int rowCount = Math.max(1, Math.max(ownedMeasures.size(), sharedMeasures.size()));
    List<UserExportRow> rows = new ArrayList<>(rowCount);
    for (int i = 0; i < rowCount; i++) {
      UserExportRow.UserExportRowBuilder builder = UserExportRow.builder();
      // User metadata appears on the user's first row only.
      if (i == 0) {
        applyUserMetadata(builder, user);
      }
      if (i < ownedMeasures.size()) {
        applyOwnedMeasure(builder, ownedMeasures.get(i));
      }
      if (i < sharedMeasures.size()) {
        applySharedMeasure(builder, sharedMeasures.get(i));
      }
      rows.add(builder.build());
    }
    return rows;
  }

  private void applyUserMetadata(UserExportRow.UserExportRowBuilder builder, MadieUser user) {
    builder
        .userDisplayName(user.getDisplayName())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .harpId(user.getHarpId())
        .emailAddress(user.getEmail())
        .userStatus(user.getStatus() == null ? null : user.getStatus().name())
        .roles(formatRoles(user.getRoles()))
        .lastLogin(formatInstant(user.getLastLoginAt()));
    // "approval" (column 8): no user-level approval concept exists yet — intentionally left blank.
  }

  private void applyOwnedMeasure(UserExportRow.UserExportRowBuilder builder, MeasureDTO measure) {
    builder
        .ownedMeasureName(measure.getMeasureName())
        .ownedMeasureVersion(measure.getVersion())
        .ownedMeasureStatus(measureStatus(measure))
        .ownedMeasureModel(measure.getModel())
        .ownedMeasureCmsId(measureCmsId(measure))
        .ownedMeasureUpdated(formatInstant(measure.getLastModifiedAt()));
  }

  private void applySharedMeasure(UserExportRow.UserExportRowBuilder builder, MeasureDTO measure) {
    builder
        .sharedMeasureName(measure.getMeasureName())
        .sharedMeasureVersion(measure.getVersion())
        .sharedMeasureStatus(measureStatus(measure))
        .sharedMeasureModel(measure.getModel())
        .sharedMeasureCmsId(measureCmsId(measure))
        .sharedMeasureOwner(measureOwner(measure))
        .sharedMeasureUpdated(formatInstant(measure.getLastModifiedAt()));
  }

  private String measureStatus(MeasureDTO measure) {
    if (measure.getMeasureMetaData() == null) {
      return null;
    }
    return measure.getMeasureMetaData().isDraft() ? "Draft" : "Versioned";
  }

  private String measureCmsId(MeasureDTO measure) {
    if (measure.getMeasureSet() == null || measure.getMeasureSet().getCmsId() == null) {
      return null;
    }
    return String.valueOf(measure.getMeasureSet().getCmsId());
  }

  private String measureOwner(MeasureDTO measure) {
    if (StringUtils.isNotBlank(measure.getOwnerDisplayName())) {
      return measure.getOwnerDisplayName();
    }
    return measure.getMeasureSet() == null ? null : measure.getMeasureSet().getOwner();
  }

  private String formatRoles(List<HarpRole> roles) {
    if (CollectionUtils.isEmpty(roles)) {
      return null;
    }
    return roles.stream()
        .map(HarpRole::getRole)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(", "));
  }

  private String formatInstant(Instant instant) {
    return instant == null ? null : DATE_TIME_FORMATTER.format(instant);
  }
}
