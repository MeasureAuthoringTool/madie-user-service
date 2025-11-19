package gov.cms.madie.user.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.access.MadieUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomMadieUserRepositoryImplTest {

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private CustomMadieUserRepositoryImpl repository;

  private UpdateResult mockUpdateResult;

  @BeforeEach
  void setUp() {
    mockUpdateResult = UpdateResult.acknowledged(1, 1L, null);
  }

  @Test
  void updateMadieUserSuccessfullyUpdatesWithValidData() {
    String harpId = "user123";
    Map<String, Object> updates = new HashMap<>();
    updates.put("email", "newemail@example.com");
    updates.put("firstName", "John");
    updates.put("lastName", "Doe");

    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(MadieUser.class)))
        .thenReturn(mockUpdateResult);

    UpdateResult result = repository.updateMadieUser(updates, harpId);

    assertNotNull(result);
    assertTrue(result.wasAcknowledged());
    assertThat(result.getModifiedCount(), is(1L));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate)
        .updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(MadieUser.class));

    Query capturedQuery = queryCaptor.getValue();
    assertNotNull(capturedQuery);
    assertThat(capturedQuery.toString(), containsString("harpId"));
    assertThat(capturedQuery.toString(), containsString(harpId));
  }

  @Test
  void updateMadieUserReturnsUnacknowledgedWhenUpdatesIsNull() {
    String harpId = "user456";

    UpdateResult result = repository.updateMadieUser(null, harpId);

    assertNotNull(result);
    assertFalse(result.wasAcknowledged());
    verify(mongoTemplate, never())
        .updateFirst(any(Query.class), any(Update.class), eq(MadieUser.class));
  }

  @Test
  void updateMadieUserReturnsUnacknowledgedWhenUpdatesIsEmpty() {
    String harpId = "user789";
    Map<String, Object> emptyUpdates = Collections.emptyMap();

    UpdateResult result = repository.updateMadieUser(emptyUpdates, harpId);

    assertNotNull(result);
    assertFalse(result.wasAcknowledged());
    verify(mongoTemplate, never())
        .updateFirst(any(Query.class), any(Update.class), eq(MadieUser.class));
  }

  @Test
  void updateMadieUserHandlesSingleFieldUpdate() {
    String harpId = "singlefield";
    Map<String, Object> updates = new HashMap<>();
    updates.put("email", "single@example.com");

    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(MadieUser.class)))
        .thenReturn(mockUpdateResult);

    UpdateResult result = repository.updateMadieUser(updates, harpId);

    assertNotNull(result);
    assertTrue(result.wasAcknowledged());
    verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(MadieUser.class));
  }
}
