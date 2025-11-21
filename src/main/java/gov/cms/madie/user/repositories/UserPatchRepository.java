package gov.cms.madie.user.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.access.MadieUser;

import java.util.Map;

public interface UserPatchRepository {

  MadieUser loginUser(MadieUser madieUser);

  UpdateResult updateMadieUser(Map<String, Object> updates, String harpId);
}
