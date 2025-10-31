package gov.cms.madie.user.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoIndexConfig {

  @Bean
  @Profile("!test")
  public CommandLineRunner createIndexes(MongoTemplate mongoTemplate) {
    return args -> {
      MongoDatabase db = mongoTemplate.getDb();
      MongoCollection<Document> collection = db.getCollection("user");

      Document indexKey = new Document("harpId", 1);
      IndexOptions options =
          new IndexOptions()
              .unique(true)
              .collation(
                  Collation.builder()
                      .locale("en")
                      .collationStrength(CollationStrength.SECONDARY) // Case-insensitive
                      .build());

      collection.createIndex(indexKey, options);
    };
  }
}
