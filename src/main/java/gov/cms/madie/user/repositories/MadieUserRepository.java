package gov.cms.madie.user.repositories;

import gov.cms.madie.models.access.MadieUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MadieUserRepository extends MongoRepository<MadieUser, String> {

  Optional<MadieUser> findByHarpId(String harpId);

  /**
   * Fetch only harpId field using projection to reduce memory usage.
   *
   * @param pageable pagination parameters
   * @return Page of MadieUser with only harpId field populated
   */
  @Query(value = "{}", fields = "{ 'harpId' : 1 }")
  Page<MadieUser> findAllHarpIds(Pageable pageable);
}
