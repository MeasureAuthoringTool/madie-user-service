package gov.cms.madie.user.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.access.MadieUser;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Map;

@Repository
public class CustomMadieUserRepositoryImpl implements CustomMadieUserRepository {

  private final MongoTemplate mongoTemplate;

  public CustomMadieUserRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public UpdateResult updateMadieUser(Map<String, Object> updates, String harpId) {
    if (CollectionUtils.isEmpty(updates)) {
      return UpdateResult.unacknowledged();
    }

    Query query = Query.query(Criteria.where("harpId").is(harpId));
    Update update = new Update();
    updates.forEach(update::set);

    return mongoTemplate.updateFirst(query, update, MadieUser.class);
  }
}
