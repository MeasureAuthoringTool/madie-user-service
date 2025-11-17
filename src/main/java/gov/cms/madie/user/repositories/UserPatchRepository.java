package gov.cms.madie.user.repositories;

import gov.cms.madie.models.access.MadieUser;

public interface UserPatchRepository {

  MadieUser loginUser(MadieUser madieUser);
}
