package gov.cms.madie.user.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Objects;

import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class MongoIndexConfigTest {
  @Test
  void createIndexesShouldCreateIndexWithCorrectOptions() throws Exception {
    // given
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    MongoDatabase mongoDatabase = mock(MongoDatabase.class);
    MongoCollection<Document> mongoCollection = mock(MongoCollection.class);
    when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection("user")).thenReturn(mongoCollection);
    MongoIndexConfig config = new MongoIndexConfig();

    // when
    config.createIndexes(mongoTemplate).run((String) null);

    // then
    ArgumentCaptor<Document> keyCaptor = ArgumentCaptor.forClass(Document.class);
    ArgumentCaptor<IndexOptions> optionsCaptor = ArgumentCaptor.forClass(IndexOptions.class);
    verify(mongoCollection, times(1)).createIndex(keyCaptor.capture(), optionsCaptor.capture());
    assertThat(keyCaptor.getValue().get("harpId"), is(1));
    assertThat(optionsCaptor.getValue().isUnique(), is(true));
    assertThat(
        Objects.requireNonNull(optionsCaptor.getValue().getCollation()).getLocale(), is("en"));
    assertThat(
        optionsCaptor.getValue().getCollation().getStrength(), is(CollationStrength.SECONDARY));
  }
}
