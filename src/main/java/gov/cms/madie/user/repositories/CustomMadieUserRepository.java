package gov.cms.madie.user.repositories;

import com.mongodb.client.result.UpdateResult;

import java.util.Map;

public interface CustomMadieUserRepository {
  UpdateResult updateMadieUser(Map<String, Object> updates, String harpId);
}
