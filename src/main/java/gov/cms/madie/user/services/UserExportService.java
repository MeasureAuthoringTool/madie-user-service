package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.LibraryDTO;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.UserExportRequest;
import gov.cms.madie.user.dto.UserExportRequestDto;
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
   * @param exportRequest the users/groups to export; when null or missing user ids, all users and
   *     all groups are exported
   * @return the generated .xlsx as bytes
   */
  public byte[] generateUserExport(String authorizationHeader, UserExportRequestDto exportRequest) {
    UserExportSelection exportSelection = normalizeExportSelection(exportRequest);
    List<UserExportRow> rows = buildRows(authorizationHeader, exportSelection);
    UserExportRequest requestBody =
        UserExportRequest.builder()
            .rows(rows)
            .ownedMeasures(exportSelection.ownedMeasures())
            .sharedMeasures(exportSelection.sharedMeasures())
            .ownedLibraries(exportSelection.ownedLibraries())
            .sharedLibraries(exportSelection.sharedLibraries())
            .build();

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
   * user's first row only. Included measure/library groups are bulk-fetched once and then fanned
   * out so that row {@code i} carries the {@code i}-th selected item from each included group; a
   * user therefore contributes {@code max(includedGroupCounts..., 1)} rows. If the bulk measure
   * lookup fails while a measure group is requested, the first row for each user carries an error
   * marker (rendered in red in the measure section), while library data is still populated when
   * available.
   *
   * @param authorizationHeader the admin caller's Authorization header, forwarded downstream
   * @param exportRequest the users/groups to export; when null or missing user ids, all users and
   *     all groups are exported
   * @return the flattened export rows across all users
   */
  public List<UserExportRow> buildRows(
      String authorizationHeader, UserExportRequestDto exportRequest) {
    return buildRows(authorizationHeader, normalizeExportSelection(exportRequest));
  }

  private List<UserExportRow> buildRows(
      String authorizationHeader, UserExportSelection exportSelection) {
    List<MadieUser> users =
        exportSelection.exportAllUsers()
            ? userService.getAllUsers()
            : userService.getUsersByHarpIds(exportSelection.userIds());
    if (CollectionUtils.isEmpty(users)) {
      return Collections.emptyList();
    }

    List<String> userHarpIds =
        users.stream()
            .map(MadieUser::getHarpId)
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .collect(Collectors.toList());

    Map<String, UserMeasuresDto> measuresByUser = Collections.emptyMap();
    if (exportSelection.shouldFetchMeasures()) {
      try {
        measuresByUser = measureServiceClient.getMeasuresForUsers(userHarpIds, authorizationHeader);
      } catch (Exception ex) {
        log.error("Unable to bulk-retrieve measures for {} user(s)", users.size(), ex);
        measuresByUser = null;
      }
    }

    Map<String, UserLibrariesDto> librariesByUser = Collections.emptyMap();
    if (exportSelection.shouldFetchLibraries()) {
      try {
        librariesByUser =
            cqlLibraryServiceClient.getLibrariesForUsers(userHarpIds, authorizationHeader);
      } catch (Exception ex) {
        log.error("Unable to bulk-retrieve libraries for {} user(s)", users.size(), ex);
        librariesByUser = null;
      }
    }

    List<UserExportRow> rows = new ArrayList<>();
    for (MadieUser user : users) {
      rows.addAll(buildRowsForUser(user, measuresByUser, librariesByUser, exportSelection));
    }
    return rows;
  }

  private List<UserExportRow> buildRowsForUser(
      MadieUser user,
      Map<String, UserMeasuresDto> measuresByUser,
      Map<String, UserLibrariesDto> librariesByUser,
      UserExportSelection exportSelection) {
    String harpKey = user.getHarpId() == null ? "" : user.getHarpId().toLowerCase();
    boolean measureFetchFailed = exportSelection.shouldFetchMeasures() && measuresByUser == null;
    UserMeasuresDto userMeasures =
        measureFetchFailed
            ? new UserMeasuresDto()
            : measuresByUser.getOrDefault(harpKey, new UserMeasuresDto());
    UserLibrariesDto userLibraries =
        librariesByUser == null
            ? new UserLibrariesDto()
            : librariesByUser.getOrDefault(harpKey, new UserLibrariesDto());

    List<MeasureDTO> ownedMeasures =
        exportSelection.ownedMeasures() && userMeasures.getOwnedMeasures() != null
            ? userMeasures.getOwnedMeasures()
            : Collections.emptyList();
    List<MeasureDTO> sharedMeasures =
        exportSelection.sharedMeasures() && userMeasures.getSharedMeasures() != null
            ? userMeasures.getSharedMeasures()
            : Collections.emptyList();
    List<LibraryDTO> ownedLibraries =
        exportSelection.ownedLibraries() && userLibraries.getOwnedLibraries() != null
            ? userLibraries.getOwnedLibraries()
            : Collections.emptyList();
    List<LibraryDTO> sharedLibraries =
        exportSelection.sharedLibraries() && userLibraries.getSharedLibraries() != null
            ? userLibraries.getSharedLibraries()
            : Collections.emptyList();

    int rowCount = 1;
    if (exportSelection.ownedMeasures()) {
      rowCount = Math.max(rowCount, ownedMeasures.size());
    }
    if (exportSelection.sharedMeasures()) {
      rowCount = Math.max(rowCount, sharedMeasures.size());
    }
    if (exportSelection.ownedLibraries()) {
      rowCount = Math.max(rowCount, ownedLibraries.size());
    }
    if (exportSelection.sharedLibraries()) {
      rowCount = Math.max(rowCount, sharedLibraries.size());
    }

    List<UserExportRow> rows = new ArrayList<>(rowCount);
    for (int i = 0; i < rowCount; i++) {
      UserExportRow.UserExportRowBuilder builder = UserExportRow.builder();
      if (i == 0) {
        applyUserMetadata(builder, user);
        if (measureFetchFailed) {
          builder.measureError(MEASURE_FETCH_ERROR_MESSAGE);
        }
      }
      if (exportSelection.ownedMeasures() && i < ownedMeasures.size()) {
        applyOwnedMeasure(builder, ownedMeasures.get(i));
      }
      if (exportSelection.sharedMeasures() && i < sharedMeasures.size()) {
        applySharedMeasure(builder, sharedMeasures.get(i));
      }
      if (exportSelection.ownedLibraries() && i < ownedLibraries.size()) {
        applyOwnedLibrary(builder, ownedLibraries.get(i));
      }
      if (exportSelection.sharedLibraries() && i < sharedLibraries.size()) {
        applySharedLibrary(builder, sharedLibraries.get(i));
      }
      rows.add(builder.build());
    }
    return rows;
  }

  private UserExportSelection normalizeExportSelection(UserExportRequestDto exportRequest) {
    if (exportRequest == null || CollectionUtils.isEmpty(exportRequest.getUserIds())) {
      return UserExportSelection.fullExport();
    }

    List<String> userIds =
        exportRequest.getUserIds().stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .toList();
    if (CollectionUtils.isEmpty(userIds)) {
      return UserExportSelection.fullExport();
    }

    return new UserExportSelection(
        userIds,
        false,
        Boolean.TRUE.equals(exportRequest.getOwnedMeasures()),
        Boolean.TRUE.equals(exportRequest.getSharedMeasures()),
        Boolean.TRUE.equals(exportRequest.getOwnedLibraries()),
        Boolean.TRUE.equals(exportRequest.getSharedLibraries()));
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

  private record UserExportSelection(
      List<String> userIds,
      boolean exportAllUsers,
      boolean ownedMeasures,
      boolean sharedMeasures,
      boolean ownedLibraries,
      boolean sharedLibraries) {

    private static UserExportSelection fullExport() {
      return new UserExportSelection(List.of(), true, true, true, true, true);
    }

    private boolean shouldFetchMeasures() {
      return ownedMeasures || sharedMeasures;
    }

    private boolean shouldFetchLibraries() {
      return ownedLibraries || sharedLibraries;
    }
  }
}
