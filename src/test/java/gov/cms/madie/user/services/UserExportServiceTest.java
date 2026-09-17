package gov.cms.madie.user.services;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.user.config.ExcelExportServiceConfig;
import gov.cms.madie.user.dto.MeasureDTO;
import gov.cms.madie.user.dto.UserExportRequest;
import gov.cms.madie.user.dto.UserExportRow;
import org.junit.jupiter.api.AfterEach;
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

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<UserExportRequest>> entityCaptor;

  private UserExportService userExportService;
  private UserExportExecutor exportExecutor;

  private static final String AUTH = "Bearer test-token";

  @BeforeEach
  void setUp() {
    exportExecutor = new UserExportExecutor(4);
    userExportService =
        new UserExportService(
            excelExportServiceConfig,
            excelExportRestTemplate,
            userService,
            measureServiceClient,
            exportExecutor);
  }

  @AfterEach
  void tearDown() {
    exportExecutor.shutdown();
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
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.OWNED, AUTH))
        .thenReturn(Collections.emptyList());
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.SHARED, AUTH))
        .thenReturn(Collections.emptyList());

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
            .lastLoginAt(Instant.parse("2026-01-15T10:30:00Z"))
            .build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.OWNED, AUTH))
        .thenReturn(Collections.emptyList());
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.SHARED, AUTH))
        .thenReturn(Collections.emptyList());

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
    assertThat(row.getApproval(), is(nullValue()));
    assertThat(row.getOwnedMeasureName(), is(nullValue()));
    assertThat(row.getMeasureError(), is(nullValue()));
  }

  @Test
  void buildRowsHandlesNullRolesStatusAndLastLogin() {
    MadieUser user = MadieUser.builder().harpId("harp2").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUser("harp2", OwnershipType.OWNED, AUTH))
        .thenReturn(Collections.emptyList());
    when(measureServiceClient.getMeasuresForUser("harp2", OwnershipType.SHARED, AUTH))
        .thenReturn(Collections.emptyList());

    UserExportRow row = userExportService.buildRows(AUTH, null).get(0);

    assertThat(row.getHarpId(), is("harp2"));
    assertThat(row.getUserStatus(), is(nullValue()));
    assertThat(row.getRoles(), is(nullValue()));
    assertThat(row.getLastLogin(), is(nullValue()));
  }

  @Test
  void buildRowsFansOutOwnedAndSharedMeasuresToMaxRows() {
    MadieUser user = MadieUser.builder().harpId("harp1").displayName("Jane Doe").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.OWNED, AUTH))
        .thenReturn(
            List.of(
                measure("Owned A", "1.0.000", true, "QI-Core v4.1.1", 1234, null),
                measure("Owned B", "2.1.000", false, "QDM v5.6", null, null)));
    when(measureServiceClient.getMeasuresForUser("harp1", OwnershipType.SHARED, AUTH))
        .thenReturn(
            List.of(measure("Shared X", "3.0.000", false, "QI-Core v4.1.1", 9876, "Owner Person")));

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
  void buildRowsEmitsRedErrorMarkerWhenMeasureFetchFails() {
    MadieUser user = MadieUser.builder().harpId("harp9").displayName("Broken User").build();
    when(userService.getAllUsers()).thenReturn(List.of(user));
    when(measureServiceClient.getMeasuresForUser(eq("harp9"), eq(OwnershipType.OWNED), any()))
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
    when(measureServiceClient.getMeasuresForUser("harp3", OwnershipType.OWNED, AUTH))
        .thenReturn(List.of(ownedNoMeta));
    when(measureServiceClient.getMeasuresForUser("harp3", OwnershipType.SHARED, AUTH))
        .thenReturn(List.of(sharedNoOwner));

    UserExportRow row = userExportService.buildRows(AUTH, null).get(0);

    assertThat(row.getOwnedMeasureName(), is("Owned NoMeta"));
    assertThat(row.getOwnedMeasureStatus(), is(nullValue())); // null measureMetaData
    assertThat(row.getOwnedMeasureCmsId(), is(nullValue())); // null measureSet
    assertThat(row.getSharedMeasureName(), is("Shared NoOwner"));
    assertThat(row.getSharedMeasureStatus(), is(nullValue())); // null measureMetaData
    assertThat(row.getSharedMeasureOwner(), is(nullValue())); // blank display name + null set
  }

  @Test
  void buildRowsProcessesMultipleUsersConcurrentlyAndPreservesOrder() {
    MadieUser userA = MadieUser.builder().harpId("harpA").displayName("Alice").build();
    MadieUser userB = MadieUser.builder().harpId("harpB").displayName("Bob").build();
    MadieUser userC = MadieUser.builder().harpId("harpC").displayName("Carol").build();
    when(userService.getAllUsers()).thenReturn(List.of(userA, userB, userC));
    when(measureServiceClient.getMeasuresForUser(anyString(), any(), any()))
        .thenReturn(Collections.emptyList());

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
  void generateUserExportOmitsAuthHeaderWhenBlank() {
    when(userService.getAllUsers()).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok("bytes".getBytes(StandardCharsets.UTF_8)));

    userExportService.generateUserExport(null, null);

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
}
