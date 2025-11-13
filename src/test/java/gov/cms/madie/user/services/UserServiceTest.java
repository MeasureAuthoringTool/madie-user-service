package gov.cms.madie.user.services;

import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.user.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock HarpProxyService harpProxyService;
  @InjectMocks private UserService userService;

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
  void refreshUserRolesAndLoginReturnsMadieUser() {
    // given
    String harpId = "bbb222";
    when(harpProxyService.getToken())
        .thenReturn(TokenResponse.builder().accessToken("fake.jwt").build());
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUserWhenTokenIsNull() {
    // given
    String harpId = "nullTokenUser";
    when(harpProxyService.getToken()).thenReturn(null);
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
    // then
    assertThat(user.getHarpId(), is(harpId));
  }

  @Test
  void refreshUserRolesAndLoginReturnsMadieUserWhenAccessTokenIsBlank() {
    // given
    String harpId = "blankTokenUser";
    when(harpProxyService.getToken()).thenReturn(TokenResponse.builder().accessToken("").build());
    // when
    MadieUser user = userService.refreshUserRolesAndLogin(harpId);
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
