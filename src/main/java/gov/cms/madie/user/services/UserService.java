package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.dto.TokenResponse;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final HarpProxyService harpProxyService;

  public MadieUser getUserByHarpId(String harpId) {
    // TODO: replace with database lookup
    return MadieUser.builder().harpId(harpId).build();
  }

  public MadieUser refreshUserRolesAndLogin(String harpId) {
    TokenResponse token = harpProxyService.getToken();
    MadieUser.MadieUserBuilder madieUserBuilder = MadieUser.builder().harpId(harpId);
    if (token == null || StringUtils.isBlank(token.getAccessToken())) {
      // bad things happened
      log.info("Unable to refresh user roles for HARP ID: {} - no token received", harpId);
      // for now, do not block user login
    } else {
      log.info("refreshing with token: {}", token.getAccessToken());
      // TODO: call HARP to refresh user roles
    }
    return madieUserBuilder.build();
  }

  public UserDetailsDto getUserDetailsByHarpId(String harpId) {
    // TODO: fetch user record and map to UserDetailsDto
    return UserDetailsDto.builder().harpId(harpId).build();
  }
}
