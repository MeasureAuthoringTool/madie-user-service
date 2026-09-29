package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.LibraryDTO;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.UserExportRequest;
import gov.cms.madie.user.dto.UserExportRow;
import gov.cms.madie.user.dto.UserLibrariesDto;
import gov.cms.madie.user.dto.UserMeasuresDto;
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserExportServiceTest {

  @Mock private ExcelExportServiceConfig excelExportServiceConfig;
  @Mock private RestTemplate excelExportRestTemplate;
  @Mock private UserService userService;
  @Mock private MeasureServiceClient measureServiceClient;
  @Mock private CqlLibraryServiceClient cqlLibraryServiceClient;

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<UserExportRequest>> entityCaptor;

  private UserExportService userExportService;

  private static final String AUTH = "Bearer test-token";

  @BeforeEach
  void setUp() {
    userExportService =
        new UserExportService(
            excelExportServiceConfig,
            excelExportRestTemplate,
            userService,
            measureServiceClient,
            cqlLibraryServiceClient);
  }

  private MeasureDTO measure(
      String name,
      String version,
      boolean draft,
      String model,
      Integer cmsId,
      String ownerDisplay) {
    return MeasureDTO.builder()
        .measureName(name)
        .version(version)
        .model(model)
        .lastModifiedAt(Instant.parse("2026-03-01T12:00:00Z"))
        .ownerDisplayName(ownerDisplay)
        .measureMetaData(MeasureDTO.MeasureMetaDataDTO.builder().draft(draft).build())
        .measureSet(MeasureDTO.MeasureSetDTO.builder().cmsId(cmsId).owner("owner-harp").build())
        .build();
  }

  private UserMeasuresDto userMeasures(List<MeasureDTO> owned, List<MeasureDTO> shared) {
    return new UserMeasuresDto(owned, shared);
  }

  private LibraryDTO library(
      String name, String version, boolean draft, String model, String ownerDisplay) {
    return new LibraryDTO(
        name,
        version,
        ownerDisplay,
        model,
        Instant.parse("2026-03-05T14:15:00Z"),
        draft,
        new LibraryDTO.LibrarySetDto("owner-harp"));
  }

  private UserLibrariesDto userLibraries(List<LibraryDTO> owned, List<LibraryDTO> shared) {
    return new UserLibrariesDto(owned, shared);
  }

  @Test
  void buildRowsReturnsEmptyWhenNoUsers() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    assertThat(userExportService.buildRows(AUTH, null), is(empty()));
  }

  @Test
  void buildRowsUsesSelectedUsersWhenHarpIdsProvided() {
    MadieUser user = MadieUser.builder().harpId("harp1").displayName("Jane Doe").build();
    List<String> harpIds = List.of("harp1");
    when(userService.getUsersByHarpIds(harpIds)).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, harpIds);

    assertThat(rows, hasSize(1));
    assertThat(rows.get(0).getUserDisplayName(), is("Jane Doe"));
    // Selected-users path must not fall back to fetching all users
    verify(userService, never()).getAllUsers();
  }

  @Test
  void buildRowsMapsUserMetadataWhenNoMeasures() {
    MadieUser user =
        MadieUser.builder()
            .harpId("harp1")
            .displayName("Jane Doe")
            .firstName("Jane")
            .lastName("Doe")
            .email("jane@example.com")
            .status(UserStatus.ACTIVE)
            .roles(
                List.of(
                    HarpRole.builder().role("MADiE-Admin").build(),
                    HarpRole.builder().role("MADiE-User").build()))
            .accessStartAt(Instant.parse("2025-12-01T08:00:00Z"))
            .lastLoginAt(Instant.parse("2026-01-15T10:30:00Z"))
            .build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // No measures -> exactly one (metadata-only) row
    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getUserDisplayName(), is("Jane Doe"));
    assertThat(row.getFirstName(), is("Jane"));
    assertThat(row.getLastName(), is("Doe"));
    assertThat(row.getHarpId(), is("harp1"));
    assertThat(row.getEmailAddress(), is("jane@example.com"));
    assertThat(row.getUserStatus(), is("ACTIVE"));
    assertThat(row.getRoles(), is("MADiE-Admin, MADiE-User"));
    assertThat(row.getLastLogin(), is("2026-01-15 10:30:00"));
    assertThat(row.getApproval(), is("2025-12-01 08:00:00"));
    assertThat(row.getOwnedMeasureName(), is(nullValue()));
    assertThat(row.getMeasureError(), is(nullValue()));
  }

  @Test
  void buildRowsHandlesNullRolesStatusAndLastLogin() {
    MadieUser user = MadieUser.builder().harpId("harp2").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    UserExportRow row = userExportService.buildRows(AUTH, null).get(0);

    assertThat(row.getHarpId(), is("harp2"));
    assertThat(row.getUserStatus(), is(nullValue()));
    assertThat(row.getRoles(), is(nullValue()));
    assertThat(row.getLastLogin(), is(nullValue()));
    assertThat(row.getApproval(), is(nullValue()));
  }

  @Test
  void buildRowsFansOutOwnedAndSharedMeasuresToMaxRows() {
    MadieUser user = MadieUser.builder().harpId("harp1").displayName("Jane Doe").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp1",
                userMeasures(
                    List.of(
                        measure("Owned A", "1.0.000", true, "QI-Core v4.1.1", 1234, null),
                        measure("Owned B", "2.1.000", false, "QDM v5.6", null, null)),
                    List.of(
                        measure(
                            "Shared X",
                            "3.0.000",
                            false,
                            "QI-Core v4.1.1",
                            9876,
                            "Owner Person")))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // max(2 owned, 1 shared) = 2 rows for this user
    assertThat(rows, hasSize(2));

    UserExportRow row0 = rows.get(0);
    // metadata only on first row
    assertThat(row0.getUserDisplayName(), is("Jane Doe"));
    assertThat(row0.getHarpId(), is("harp1"));
    // owned measure 0
    assertThat(row0.getOwnedMeasureName(), is("Owned A"));
    assertThat(row0.getOwnedMeasureVersion(), is("1.0.000"));
    assertThat(row0.getOwnedMeasureStatus(), is("Draft"));
    assertThat(row0.getOwnedMeasureModel(), is("QI-Core v4.1.1"));
    assertThat(row0.getOwnedMeasureCmsId(), is("1234"));
    assertThat(row0.getOwnedMeasureUpdated(), is("2026-03-01 12:00:00"));
    // shared measure 0 (owner uses display name)
    assertThat(row0.getSharedMeasureName(), is("Shared X"));
    assertThat(row0.getSharedMeasureStatus(), is("Versioned"));
    assertThat(row0.getSharedMeasureCmsId(), is("9876"));
    assertThat(row0.getSharedMeasureOwner(), is("Owner Person"));

    UserExportRow row1 = rows.get(1);
    // continuation row: no metadata, no shared measure, owned measure 1
    assertThat(row1.getUserDisplayName(), is(nullValue()));
    assertThat(row1.getHarpId(), is(nullValue()));
    assertThat(row1.getOwnedMeasureName(), is("Owned B"));
    assertThat(row1.getOwnedMeasureStatus(), is("Versioned"));
    assertThat(row1.getOwnedMeasureCmsId(), is(nullValue())); // null cmsId
    assertThat(row1.getSharedMeasureName(), is(nullValue()));
  }

  @Test
  void buildRowsFansOutOwnedAndSharedLibrariesToMaxRows() {
    MadieUser user = MadieUser.builder().harpId("harp1").displayName("Jane Doe").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp1",
                userLibraries(
                    List.of(
                        library("Owned Library A", "1.0.000", true, "QI-Core v4.1.1", null),
                        library("Owned Library B", "2.0.000", false, "QDM v5.6", null)),
                    List.of(
                        library(
                            "Shared Library X",
                            "3.0.000",
                            false,
                            "QI-Core v4.1.1",
                            "Owner Person")))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(2));

    UserExportRow row0 = rows.get(0);
    assertThat(row0.getUserDisplayName(), is("Jane Doe"));
    assertThat(row0.getOwnedLibraryName(), is("Owned Library A"));
    assertThat(row0.getOwnedLibraryVersion(), is("1.0.000"));
    assertThat(row0.getOwnedLibraryStatus(), is("Draft"));
    assertThat(row0.getOwnedLibraryModel(), is("QI-Core v4.1.1"));
    assertThat(row0.getOwnedLibraryUpdated(), is("2026-03-05 14:15:00"));
    assertThat(row0.getSharedLibraryName(), is("Shared Library X"));
    assertThat(row0.getSharedLibraryStatus(), is("Versioned"));
    assertThat(row0.getSharedLibraryOwner(), is("Owner Person"));

    UserExportRow row1 = rows.get(1);
    assertThat(row1.getUserDisplayName(), is(nullValue()));
    assertThat(row1.getOwnedLibraryName(), is("Owned Library B"));
    assertThat(row1.getOwnedLibraryStatus(), is("Versioned"));
    assertThat(row1.getSharedLibraryName(), is(nullValue()));
  }

  @Test
  void buildRowsFansOutAcrossMeasuresAndLibrariesUsingLargestBucket() {
    MadieUser user = MadieUser.builder().harpId("harp1").displayName("Jane Doe").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp1",
                userMeasures(
                    List.of(
                        measure("Owned Measure", "1.0.000", true, "QI-Core v4.1.1", 1234, null)),
                    List.of())));
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp1",
                userLibraries(
                    List.of(
                        library("Owned Library A", "1.0.000", true, "QI-Core v4.1.1", null),
                        library("Owned Library B", "2.0.000", false, "QDM v5.6", null),
                        library("Owned Library C", "3.0.000", false, "QDM v5.6", null)),
                    List.of())));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(3));
    assertThat(rows.get(0).getOwnedMeasureName(), is("Owned Measure"));
    assertThat(rows.get(0).getOwnedLibraryName(), is("Owned Library A"));
    assertThat(rows.get(1).getOwnedMeasureName(), is(nullValue()));
    assertThat(rows.get(1).getOwnedLibraryName(), is("Owned Library B"));
    assertThat(rows.get(2).getOwnedLibraryName(), is("Owned Library C"));
  }

  @Test
  void buildRowsEmitsRedErrorMarkerWhenMeasureFetchFails() {
    MadieUser user = MadieUser.builder().harpId("harp9").displayName("Broken User").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), any()))
        .thenThrow(new RestClientException("measure-service down"));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getUserDisplayName(), is("Broken User"));
    assertThat(row.getHarpId(), is("harp9"));
    assertThat(row.getMeasureError(), is("Unable to retrieve this user"));
    assertThat(row.getOwnedMeasureName(), is(nullValue()));
    assertThat(row.getSharedMeasureName(), is(nullValue()));
  }

  @Test
  void buildRowsStillPopulatesLibrariesWhenMeasureFetchFails() {
    MadieUser user = MadieUser.builder().harpId("harp9").displayName("Broken User").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), any()))
        .thenThrow(new RestClientException("measure-service down"));
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp9",
                userLibraries(
                    List.of(library("Owned Library", "1.0.000", true, "QI-Core v4.1.1", null)),
                    List.of(
                        library("Shared Library", "2.0.000", false, "QDM v5.6", "Owner Person")))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getMeasureError(), is("Unable to retrieve this user"));
    assertThat(row.getOwnedLibraryName(), is("Owned Library"));
    assertThat(row.getSharedLibraryName(), is("Shared Library"));
    assertThat(row.getSharedLibraryOwner(), is("Owner Person"));
  }

  @Test
  void buildRowsHandlesMeasuresWithNullMetadataAndMeasureSet() {
    MadieUser user = MadieUser.builder().harpId("harp3").displayName("Nulls").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));

    // Owned measure with no measureMetaData and no measureSet -> null status and null cmsId.
    MeasureDTO ownedNoMeta =
        MeasureDTO.builder()
            .measureName("Owned NoMeta")
            .version("1.0.000")
            .model("QI-Core v4.1.1")
            .build();
    // Shared measure with blank ownerDisplayName and no measureSet -> null owner and null status.
    MeasureDTO sharedNoOwner =
        MeasureDTO.builder()
            .measureName("Shared NoOwner")
            .version("2.0.000")
            .model("QDM v5.6")
            .ownerDisplayName("   ")
            .build();
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(Map.of("harp3", userMeasures(List.of(ownedNoMeta), List.of(sharedNoOwner))));

    UserExportRow row = userExportService.buildRows(AUTH, null).get(0);

    assertThat(row.getOwnedMeasureName(), is("Owned NoMeta"));
    assertThat(row.getOwnedMeasureStatus(), is(nullValue())); // null measureMetaData
    assertThat(row.getOwnedMeasureCmsId(), is(nullValue())); // null measureSet
    assertThat(row.getSharedMeasureName(), is("Shared NoOwner"));
    assertThat(row.getSharedMeasureStatus(), is(nullValue())); // null measureMetaData
    assertThat(row.getSharedMeasureOwner(), is(nullValue())); // blank display name + null set
  }

  @Test
  void buildRowsPreservesUserOrder() {
    MadieUser userA = MadieUser.builder().harpId("harpA").displayName("Alice").build();
    MadieUser userB = MadieUser.builder().harpId("harpB").displayName("Bob").build();
    MadieUser userC = MadieUser.builder().harpId("harpC").displayName("Carol").build();
    when(userService.getAllUsers()).thenReturn(List.of(userA, userB, userC));
    when(measureServiceClient.getMeasuresForUsers(anyList(), any())).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // One metadata row per user, in the original user order.
    assertThat(rows, hasSize(3));
    assertThat(rows.get(0).getUserDisplayName(), is("Alice"));
    assertThat(rows.get(1).getUserDisplayName(), is("Bob"));
    assertThat(rows.get(2).getUserDisplayName(), is("Carol"));
  }

  @Test
  void generateUserExportSendsRowsAndReturnsBytes() {
    byte[] expectedBytes = "fake-xlsx-bytes".getBytes(StandardCharsets.UTF_8);
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            urlCaptor.capture(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] actual = userExportService.generateUserExport(AUTH, null);

    assertThat(actual, is(expectedBytes));
    assertThat(urlCaptor.getValue(), is("http://excel-export:3000/api/excel/user-export"));

    HttpEntity<UserExportRequest> sentEntity = entityCaptor.getValue();
    assertThat(sentEntity.getBody(), notNullValue());
    assertThat(sentEntity.getBody().getRows(), is(empty()));

    HttpHeaders headers = sentEntity.getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(
        headers.getAccept(), hasItem(MediaType.parseMediaType(UserExportService.XLSX_MEDIA_TYPE)));
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION), is(AUTH));
  }

  @Test
  void generateUserExportOmitsAuthHeaderWhenBlankOrNull() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok("bytes".getBytes(StandardCharsets.UTF_8)));

    userExportService.generateUserExport(null, null);
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), nullValue());

    userExportService.generateUserExport("   ", null);
    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), nullValue());
  }

  @Test
  void generateUserExportThrowsBadGatewayOnNonSuccessResponse() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(byte[].class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> userExportService.generateUserExport(AUTH, null));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void generateUserExportThrowsBadGatewayWhenDownstreamUnreachable() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(byte[].class)))
        .thenThrow(new RestClientException("connection refused"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> userExportService.generateUserExport(AUTH, null));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void buildRowsStillPopulatesMeasuresWhenLibraryFetchFails() {
    MadieUser user = MadieUser.builder().harpId("harp5").displayName("Library Broken").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp5",
                userMeasures(
                    List.of(
                        measure("Owned Measure", "1.0.000", true, "QI-Core v4.1.1", 5678, null)),
                    List.of())));
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), any()))
        .thenThrow(new RestClientException("library-service down"));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    // Measure data should still be populated despite library fetch failure
    assertThat(row.getOwnedMeasureName(), is("Owned Measure"));
    assertThat(row.getOwnedMeasureStatus(), is("Draft"));
    assertThat(row.getOwnedMeasureCmsId(), is("5678"));
    // No error marker since only library fetch failed, not measure
    assertThat(row.getMeasureError(), is(nullValue()));
    // Library fields should be null/empty
    assertThat(row.getOwnedLibraryName(), is(nullValue()));
  }

  @Test
  void buildRowsHandlesUserWithNullHarpId() {
    MadieUser user = MadieUser.builder().displayName("No HARP ID").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getUserDisplayName(), is("No HARP ID"));
    assertThat(row.getHarpId(), is(nullValue()));
    // Should not fail even with null harpId
    assertThat(row.getOwnedMeasureName(), is(nullValue()));
  }

  @Test
  void buildRowsNormalizesHarpIdToLowercase() {
    MadieUser user = MadieUser.builder().harpId("HARP1").displayName("Upper Case").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp1",
                userMeasures(
                    List.of(measure("Measure", "1.0.000", true, "QI-Core v4.1.1", null, null)),
                    List.of())));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // Verify that the user with uppercase HARP ID matched the lowercase key
    assertThat(rows, hasSize(1));
    assertThat(rows.get(0).getOwnedMeasureName(), is("Measure"));
  }

  @Test
  void buildRowsHandlesLibrariesWithNullLibrarySet() {
    MadieUser user = MadieUser.builder().harpId("harp6").displayName("Lib Nulls").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    // Library with null librarySet
    LibraryDTO libNoSet =
        new LibraryDTO(
            "Lib No Set",
            "1.0.000",
            null,
            "QI-Core v4.1.1",
            Instant.parse("2026-03-05T14:15:00Z"),
            false,
            null);
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(Map.of("harp6", userLibraries(List.of(libNoSet), List.of())));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getOwnedLibraryName(), is("Lib No Set"));
    assertThat(row.getOwnedLibraryStatus(), is("Versioned")); // isDraft=false
    assertThat(row.getOwnedLibraryVersion(), is("1.0.000"));
  }

  @Test
  void buildRowsHandlesLibraryOwnerResolution() {
    MadieUser user1 = MadieUser.builder().harpId("harp7").displayName("Owner Fallback").build();
    MadieUser user2 = MadieUser.builder().harpId("harp8").displayName("No Owner").build();
    when(userService.getAllUsers()).thenReturn(List.of(user1, user2));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    LibraryDTO libBlankOwner =
        new LibraryDTO(
            "Lib Blank Owner",
            "1.0.000",
            "   ",
            "QI-Core v4.1.1",
            Instant.parse("2026-03-05T14:15:00Z"),
            true,
            new LibraryDTO.LibrarySetDto("set-owner"));

    LibraryDTO libNoOwner =
        new LibraryDTO(
            "Lib No Owner",
            "1.0.000",
            null,
            "QI-Core v4.1.1",
            Instant.parse("2026-03-05T14:15:00Z"),
            false,
            null);

    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp7", userLibraries(List.of(), List.of(libBlankOwner)),
                "harp8", userLibraries(List.of(), List.of(libNoOwner))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(2));
    assertThat(rows.get(0).getSharedLibraryOwner(), is("set-owner"));
    assertThat(rows.get(1).getSharedLibraryOwner(), is(nullValue()));
  }

  @Test
  void buildRowsHandlesVariousRoleCombinations() {
    MadieUser emptyRoles =
        MadieUser.builder()
            .harpId("harp10")
            .displayName("Empty Roles")
            .roles(Collections.emptyList())
            .build();
    MadieUser singleRole =
        MadieUser.builder()
            .harpId("harp11")
            .displayName("Single Role")
            .roles(List.of(HarpRole.builder().role("Admin").build()))
            .build();
    MadieUser blankAndValidRoles =
        MadieUser.builder()
            .harpId("harp12")
            .displayName("Blank Roles")
            .roles(
                List.of(
                    HarpRole.builder().role("  ").build(),
                    HarpRole.builder().role("Real Role").build(),
                    HarpRole.builder().role("").build()))
            .build();
    when(userService.getAllUsers()).thenReturn(List.of(emptyRoles, singleRole, blankAndValidRoles));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(3));
    assertThat(rows.get(0).getRoles(), is(nullValue()));
    assertThat(rows.get(1).getRoles(), is("Admin"));
    assertThat(rows.get(2).getRoles(), is("Real Role"));
  }

  @Test
  void buildRowsHandlesNullInstantFields() {
    MadieUser user =
        MadieUser.builder()
            .harpId("harp13")
            .displayName("No Timestamps")
            .accessStartAt(null)
            .lastLoginAt(null)
            .build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getApproval(), is(nullValue()));
    assertThat(row.getLastLogin(), is(nullValue()));
  }

  @Test
  void generateUserExportReturnsNullBodyHandling() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(null));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> userExportService.generateUserExport(AUTH, null));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void buildRowsWithBothMeasureAndLibraryFetchFailures() {
    MadieUser user = MadieUser.builder().harpId("harp14").displayName("Both Down").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUsers(anyList(), any()))
        .thenThrow(new RestClientException("measure-service down"));
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), any()))
        .thenThrow(new RestClientException("library-service down"));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows, hasSize(1));
    UserExportRow row = rows.get(0);
    assertThat(row.getUserDisplayName(), is("Both Down"));
    assertThat(row.getMeasureError(), is("Unable to retrieve this user"));
    assertThat(row.getOwnedMeasureName(), is(nullValue()));
    assertThat(row.getOwnedLibraryName(), is(nullValue()));
  }

  @Test
  void buildRowsHandlesMultipleUsersWithMixedDataAvailability() {
    MadieUser user1 = MadieUser.builder().harpId("harp15").displayName("User 1").build();
    MadieUser user2 = MadieUser.builder().harpId("harp16").displayName("User 2").build();
    when(userService.getAllUsers()).thenReturn(List.of(user1, user2));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp15",
                userMeasures(
                    List.of(measure("Measure1", "1.0.000", true, "QI-Core v4.1.1", null, null)),
                    List.of())));
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp16",
                userLibraries(
                    List.of(library("Library1", "2.0.000", false, "QDM v5.6", null)), List.of())));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // User 1 has measure, User 2 has library
    assertThat(rows, hasSize(2));
    assertThat(rows.get(0).getUserDisplayName(), is("User 1"));
    assertThat(rows.get(0).getOwnedMeasureName(), is("Measure1"));
    assertThat(rows.get(0).getOwnedLibraryName(), is(nullValue()));
    assertThat(rows.get(1).getUserDisplayName(), is("User 2"));
    assertThat(rows.get(1).getOwnedMeasureName(), is(nullValue()));
    assertThat(rows.get(1).getOwnedLibraryName(), is("Library1"));
  }

  @Test
  void buildRowsHandlesLibraryStatusDraftAndVersioned() {
    MadieUser draftUser = MadieUser.builder().harpId("harp17").displayName("Draft Lib").build();
    MadieUser versionedUser =
        MadieUser.builder().harpId("harp18").displayName("Versioned Lib").build();
    when(userService.getAllUsers()).thenReturn(List.of(draftUser, versionedUser));
    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH))).thenReturn(Map.of());

    LibraryDTO draftLib = library("Draft Library", "1.0.000", true, "QI-Core v4.1.1", null);
    LibraryDTO versionedLib = library("Versioned Library", "2.0.000", false, "QDM v5.6", null);
    when(cqlLibraryServiceClient.getLibrariesForUsers(anyList(), eq(AUTH)))
        .thenReturn(
            Map.of(
                "harp17", userLibraries(List.of(draftLib), List.of()),
                "harp18", userLibraries(List.of(), List.of(versionedLib))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    assertThat(rows.get(0).getOwnedLibraryStatus(), is("Draft"));
    assertThat(rows.get(1).getSharedLibraryStatus(), is("Versioned"));
  }

  @Test
  void buildRowsHandlesMeasureOwnerWithDisplayName() {
    MadieUser user = MadieUser.builder().harpId("harp19").displayName("Owner Display").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));

    MeasureDTO measureWithOwnerDisplay =
        MeasureDTO.builder()
            .measureName("With Owner Display")
            .version("1.0.000")
            .model("QI-Core v4.1.1")
            .lastModifiedAt(Instant.parse("2026-03-01T12:00:00Z"))
            .ownerDisplayName("John Doe") // Has display name
            .measureMetaData(MeasureDTO.MeasureMetaDataDTO.builder().draft(false).build())
            .measureSet(MeasureDTO.MeasureSetDTO.builder().owner("j.doe").build())
            .build();

    when(measureServiceClient.getMeasuresForUsers(anyList(), eq(AUTH)))
        .thenReturn(Map.of("harp19", userMeasures(List.of(), List.of(measureWithOwnerDisplay))));

    List<UserExportRow> rows = userExportService.buildRows(AUTH, null);

    // Should use ownerDisplayName over measureSet owner
    assertThat(rows.get(0).getSharedMeasureOwner(), is("John Doe"));
  }
}
