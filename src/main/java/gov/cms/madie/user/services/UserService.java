package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.UserDetailsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {
  public MadieUser getUserByHarpId(String harpId) {
    // TODO: replace with database lookup
    return MadieUser.builder().harpId(harpId).build();
  }

  public MadieUser refreshUserDetailsAndLogin(String harpId) {
    // TODO: call HARP to refresh user details
    return MadieUser.builder().harpId(harpId).build();
  }

  public UserDetailsDto getUserDetailsByHarpId(String harpId) {
    // TODO: fetch user record and map to UserDetailsDto
    return UserDetailsDto.builder().harpId(harpId).build();
  }
}
