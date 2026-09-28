package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.LibraryDTO;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.UserExportRequest;
import gov.cms.madie.user.dto.UserExportRow;
import gov.cms.madie.user.dto.UserLibrariesDto;
import gov.cms.madie.user.dto.UserMeasuresDto;
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
import java.util.Map;
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
  private final CqlLibraryServiceClient cqlLibraryServiceClient;

  public UserExportService(
      ExcelExportServiceConfig excelExportServiceConfig,
      @Qualifier("excelExportRestTemplate") RestTemplate excelExportRestTemplate,
      UserService userService,
      MeasureServiceClient measureServiceClient,
      CqlLibraryServiceClient cqlLibraryServiceClient) {
    this.excelExportServiceConfig = excelExportServiceConfig;
    this.excelExportRestTemplate = excelExportRestTemplate;
    this.userService = userService;
    this.measureServiceClient = measureServiceClient;
    this.cqlLibraryServiceClient = cqlLibraryServiceClient;
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
   * measure-service in a single bulk call and fanned out so that row {@code i} carries the {@code
   * i}-th owned/shared measure and owned/shared library; a user therefore contributes {@code
   * max(ownedMeasureCount, sharedMeasureCount, ownedLibraryCount, sharedLibraryCount, 1)} rows. If
   * the bulk measure lookup fails, the first row for each user carries an error marker (rendered in
   * red in the Owned Measure Name column), while library data is still populated when available.
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

    // Fetch every user's owned & shared measures in a single bulk call to measure-service,
    List<String> userHarpIds =
        users.stream()
            .map(MadieUser::getHarpId)
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    Map<String, UserMeasuresDto> measuresByUser;
    try {
      measuresByUser = measureServiceClient.getMeasuresForUsers(userHarpIds, authorizationHeader);
    } catch (Exception ex) {
      log.error("Unable to bulk-retrieve measures for {} user(s)", users.size(), ex);
      measuresByUser = null;
    }

    Map<String, UserLibrariesDto> librariesByUser;
    try {
      librariesByUser =
          cqlLibraryServiceClient.getLibrariesForUsers(userHarpIds, authorizationHeader);
    } catch (Exception ex) {
      log.error("Unable to bulk-retrieve libraries for {} user(s)", users.size(), ex);
      librariesByUser = null;
    }

    List<UserExportRow> rows = new ArrayList<>();
    for (MadieUser user : users) {
      rows.addAll(buildRowsForUser(user, measuresByUser, librariesByUser));
    }
    return rows;
  }

  private List<UserExportRow> buildRowsForUser(
      MadieUser user,
      Map<String, UserMeasuresDto> measuresByUser,
      Map<String, UserLibrariesDto> librariesByUser) {
    String harpKey = user.getHarpId() == null ? "" : user.getHarpId().toLowerCase();
    boolean measureFetchFailed = measuresByUser == null;
    UserMeasuresDto userMeasures =
        measureFetchFailed
            ? new UserMeasuresDto()
            : measuresByUser.getOrDefault(harpKey, new UserMeasuresDto());
    UserLibrariesDto userLibraries =
        librariesByUser == null
            ? new UserLibrariesDto()
            : librariesByUser.getOrDefault(harpKey, new UserLibrariesDto());
    List<MeasureDTO> ownedMeasures =
        userMeasures.getOwnedMeasures() == null
            ? Collections.emptyList()
            : userMeasures.getOwnedMeasures();
    List<MeasureDTO> sharedMeasures =
        userMeasures.getSharedMeasures() == null
            ? Collections.emptyList()
            : userMeasures.getSharedMeasures();
    List<LibraryDTO> ownedLibraries =
        userLibraries.getOwnedLibraries() == null
            ? Collections.emptyList()
            : userLibraries.getOwnedLibraries();
    List<LibraryDTO> sharedLibraries =
        userLibraries.getSharedLibraries() == null
            ? Collections.emptyList()
            : userLibraries.getSharedLibraries();

    int rowCount =
        Math.max(
            1,
            Math.max(
                Math.max(ownedMeasures.size(), sharedMeasures.size()),
                Math.max(ownedLibraries.size(), sharedLibraries.size())));
    List<UserExportRow> rows = new ArrayList<>(rowCount);
    for (int i = 0; i < rowCount; i++) {
      UserExportRow.UserExportRowBuilder builder = UserExportRow.builder();
      // User metadata appears on the user's first row only.
      if (i == 0) {
        applyUserMetadata(builder, user);
        if (measureFetchFailed) {
          builder.measureError(MEASURE_FETCH_ERROR_MESSAGE);
        }
      }
      if (i < ownedMeasures.size()) {
        applyOwnedMeasure(builder, ownedMeasures.get(i));
      }
      if (i < sharedMeasures.size()) {
        applySharedMeasure(builder, sharedMeasures.get(i));
      }
      if (i < ownedLibraries.size()) {
        applyOwnedLibrary(builder, ownedLibraries.get(i));
      }
      if (i < sharedLibraries.size()) {
        applySharedLibrary(builder, sharedLibraries.get(i));
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
        .approval(formatInstant(user.getAccessStartAt()))
        .lastLogin(formatInstant(user.getLastLoginAt()));
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

  private void applyOwnedLibrary(UserExportRow.UserExportRowBuilder builder, LibraryDTO library) {
    builder
        .ownedLibraryName(library.getCqlLibraryName())
        .ownedLibraryVersion(library.getVersion())
        .ownedLibraryStatus(libraryStatus(library))
        .ownedLibraryModel(library.getModel())
        .ownedLibraryUpdated(formatInstant(library.getLastModifiedAt()));
  }

  private void applySharedLibrary(UserExportRow.UserExportRowBuilder builder, LibraryDTO library) {
    builder
        .sharedLibraryName(library.getCqlLibraryName())
        .sharedLibraryVersion(library.getVersion())
        .sharedLibraryStatus(libraryStatus(library))
        .sharedLibraryModel(library.getModel())
        .sharedLibraryOwner(libraryOwner(library))
        .sharedLibraryUpdated(formatInstant(library.getLastModifiedAt()));
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

  private String libraryStatus(LibraryDTO library) {
    return library.isDraft() ? "Draft" : "Versioned";
  }

  private String libraryOwner(LibraryDTO library) {
    if (StringUtils.isNotBlank(library.getOwnerDisplayName())) {
      return library.getOwnerDisplayName();
    }
    return library.getLibrarySet() == null ? null : library.getLibrarySet().getOwner();
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
