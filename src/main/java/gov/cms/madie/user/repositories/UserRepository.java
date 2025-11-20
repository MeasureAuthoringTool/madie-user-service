package gov.cms.madie.user.repositories;

import gov.cms.madie.models.access.MadieUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<MadieUser, String>, UserPatchRepository {

  Optional<MadieUser> findByHarpId(String harpId);
}
