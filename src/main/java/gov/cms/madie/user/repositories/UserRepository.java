package gov.cms.madie.user.repositories;

import gov.cms.madie.models.access.MadieUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<MadieUser, String>, UserPatchRepository {

  Optional<MadieUser> findByHarpId(String harpId);

  /**
   * Count how many MadieUsers exist for the provided list of HARP IDs.
   *
   * @param harpIds list of HARP IDs to check
   * @return count of existing users
   */
  @Query(value = "{ 'harpId': { $in: ?0 } }", count = true)
  int countByHarpIdIn(java.util.List<String> harpIds);

  /**
   * Fetch only harpId field using projection to reduce memory usage.
   *
   * @param pageable pagination parameters
   * @return Page of MadieUser with only harpId field populated
   */
  @Query(value = "{}", fields = "{ 'harpId' : 1 }")
  Page<MadieUser> findAllHarpIds(Pageable pageable);
}
