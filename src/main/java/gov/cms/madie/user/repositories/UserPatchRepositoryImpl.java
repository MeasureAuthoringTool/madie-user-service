package gov.cms.madie.user.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.access.MadieUser;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserPatchRepositoryImpl implements UserPatchRepository {

  private final MongoTemplate mongoTemplate;

  @Override
  public MadieUser loginUser(@NotNull MadieUser madieUser) {
    Objects.requireNonNull(madieUser.getHarpId());

    Query query = new Query(Criteria.where("harpId").is(madieUser.getHarpId().toLowerCase()));

    Update update = new Update();
    Instant now = Instant.now();

    // Set lastModifiedAt for both create and update operations
    update.set("lastModifiedAt", now);
    update.set("lastLoginAt", now);

    // Set createdAt only on insert (first time creation)
    update.setOnInsert("createdAt", now);

    if (!CollectionUtils.isEmpty(madieUser.getRoles())) {
      update.set("roles", madieUser.getRoles());
    } else {
      update.unset("roles");
    }
    if (madieUser.getStatus() != null) {
      update.set("status", madieUser.getStatus());
    } else {
      update.unset("status");
    }
    if (madieUser.getAccessStartAt() != null) {
      update.set("accessStartAt", madieUser.getAccessStartAt());
    } else {
      update.unset("accessStartAt");
    }

    FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, MadieUser.class);
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
