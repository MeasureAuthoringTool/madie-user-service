package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.dto.UserDetailsDto;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class UserServiceTest {
  private final UserService userService = new UserService();

  @Test
  void getUserByHarpIdReturnsMadieUser() {
    // given
    String harpId = "aaa111";
    // when
    MadieUser user = userService.getUserByHarpId(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void refreshUserDetailsAndLoginReturnsMadieUser() {
    // given
    String harpId = "bbb222";
    // when
    MadieUser user = userService.refreshUserDetailsAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void getUserDetailsByHarpIdReturnsUserDetailsDto() {
    // given
    String harpId = "ccc333";
    // when
    UserDetailsDto details = userService.getUserDetailsByHarpId(harpId);
    // then
    assertThat(details.getHarpId(), is(harpId));
  }
}
