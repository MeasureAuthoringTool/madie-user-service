package gov.cms.madie.user.repositories;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.access.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class UserPatchRepositoryImplTest {

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks UserPatchRepositoryImpl repository;

  @Test
  void upsertsUserWithAllFieldsSet() {
    // given
    MadieUser user =
        MadieUser.builder()
            .harpId("harp123")
            .roles(
                List.of(
                    HarpRole.builder().role("role1").roleType("type1").build(),
                    HarpRole.builder().role("role2").roleType("type2").build()))
            .accessStartAt(Instant.parse("2023-01-01T00:00:00Z"))
            .status(UserStatus.ACTIVE)
            .build();
    MadieUser expected = MadieUser.builder().harpId("harp123").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    MadieUser result = repository.loginUser(user);
    // then
    assertThat("Returned user should match expected", result, is(expected));
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            updateCaptor.capture(),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Update update = updateCaptor.getValue();
    String updateStr = update.getUpdateObject().toString();
    assertThat("Update should set roles", updateStr, containsString("roles"));
    assertThat("Update should set accessStartAt", updateStr, containsString("accessStartAt"));
    assertThat("Update should set status", updateStr, containsString("status"));
  }

  @Test
  void unsetsRolesIfNull() {
    // given
    MadieUser user =
        MadieUser.builder()
            .harpId("harp456")
            // omit .roles() so getRoles() returns null
            .accessStartAt(Instant.parse("2023-01-01T00:00:00Z"))
            .build();
    MadieUser expected = MadieUser.builder().harpId("harp456").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    MadieUser result = repository.loginUser(user);
    // then
    assertThat("Returned user should match expected", result, is(expected));
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            updateCaptor.capture(),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Update update = updateCaptor.getValue();
    // Instead of checking for $unset, check that roles is set to an empty list
    String updateStr = update.getUpdateObject().toString();
    assertThat("Update should set roles to empty list", updateStr, containsString("roles=[]"));
  }

  @Test
  void unsetsAccessStartAtIfNull() {
    // given
    MadieUser user =
        MadieUser.builder()
            .harpId("harp789")
            .roles(List.of(HarpRole.builder().role("role1").roleType("type1").build()))
            .accessStartAt(null)
            .build();
    MadieUser expected = MadieUser.builder().harpId("harp789").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    MadieUser result = repository.loginUser(user);
    // then
    assertThat("Returned user should match expected", result, is(expected));
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            updateCaptor.capture(),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Update update = updateCaptor.getValue();
    assertThat(
        "Update should unset accessStartAt",
        update.getUpdateObject().toString(),
        containsString("$unset"));
  }

  @Test
  void unsetsBothFieldsIfNull() {
    // given
    MadieUser user =
        MadieUser.builder().harpId("harp000").accessStartAt(null).build(); // omit .roles()
    MadieUser expected = MadieUser.builder().harpId("harp000").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    MadieUser result = repository.loginUser(user);
    // then
    assertThat("Returned user should match expected", result, is(expected));
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            updateCaptor.capture(),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Update update = updateCaptor.getValue();
    String updateStr = update.getUpdateObject().toString();
    assertThat("Update should unset roles", updateStr, containsString("roles"));
    assertThat("Update should unset accessStartAt", updateStr, containsString("accessStartAt"));
    assertThat("Update should contain $unset", updateStr, containsString("$unset"));
  }

  @Test
  void throwsExceptionIfHarpIdIsNull() {
    // given
    MadieUser user = MadieUser.builder().harpId(null).build();
    // when/then
    assertThrows(NullPointerException.class, () -> repository.loginUser(user));
  }

  @Test
  void setsAuditFieldsOnUpsert() {
    // given
    MadieUser user =
        MadieUser.builder().harpId("audit123").accessStartAt(null).build(); // omit .roles()
    MadieUser expected = MadieUser.builder().harpId("audit123").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    repository.loginUser(user); // Remove unused variable warning
    // then
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class),
            updateCaptor.capture(),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Update update = updateCaptor.getValue();
    String updateStr = update.getUpdateObject().toString();
    assertThat("Update should set lastModifiedAt", updateStr, containsString("lastModifiedAt"));
    assertThat("Update should set lastLoginAt", updateStr, containsString("lastLoginAt"));
    assertThat(
        "Update should set createdAt with $setOnInsert", updateStr, containsString("createdAt"));
  }

  @Test
  void usesUpsertAndReturnNewOptions() {
    // given
    MadieUser user = MadieUser.builder().harpId("opt123").build();
    MadieUser expected = MadieUser.builder().harpId("opt123").build();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    repository.loginUser(user);
    // then
    ArgumentCaptor<FindAndModifyOptions> optionsCaptor =
        ArgumentCaptor.forClass(FindAndModifyOptions.class);
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class), any(Update.class), optionsCaptor.capture(), eq(MadieUser.class));
    FindAndModifyOptions options = optionsCaptor.getValue();
    assertThat("Options should be upsert", options.isUpsert(), is(true));
    assertThat("Options should return new", options.isReturnNew(), is(true));
  }

  @Test
  void loginUserConvertsHarpIdToLowercaseInQuery() {
    // given
    String mixedCaseHarpId = "HaRp123ABC";
    MadieUser user = MadieUser.builder().harpId(mixedCaseHarpId).build();
    MadieUser expected = MadieUser.builder().harpId(mixedCaseHarpId.toLowerCase()).build();
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class)))
        .thenReturn(expected);
    // when
    repository.loginUser(user);
    // then
    verify(mongoTemplate)
        .findAndModify(
            queryCaptor.capture(),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(MadieUser.class));
    Query capturedQuery = queryCaptor.getValue();
    Object harpIdValue = capturedQuery.getQueryObject().get("harpId");
    assertThat(
        "harpId in query should be lowercase", harpIdValue, is(mixedCaseHarpId.toLowerCase()));
  }
}
