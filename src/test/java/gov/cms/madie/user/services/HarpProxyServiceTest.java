package gov.cms.madie.user.services;

import gov.cms.madie.user.config.HarpConfig;
import gov.cms.madie.user.dto.TokenResponse;
import gov.cms.madie.user.dto.UserDetailsResponse;
import gov.cms.madie.user.dto.UserRolesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarpProxyServiceTest {

    @Mock private HarpConfig harpConfig;
    @Mock private RestTemplate harpRestTemplate;
    @InjectMocks private HarpProxyService harpProxyService;

    @BeforeEach
    void setUp() {
        HarpConfig.Token tokenConfig = new HarpConfig.Token();
        tokenConfig.setClientId("clientId");
        tokenConfig.setSecret("secret");
        tokenConfig.setScope("scope");
        tokenConfig.setUri("/token");

//        when(harpConfig.getToken()).thenReturn(tokenConfig);
//        when(harpConfig.getBaseUrl()).thenReturn("http://localhost");
//        when(harpConfig.getProgramName()).thenReturn("TestProgram");
    }

    @Test
    void getTokenReturnsTokenResponse() {
        // given
        TokenResponse expected = TokenResponse.builder().accessToken("token").build();
        when(harpConfig.getToken()).thenReturn(HarpConfig.Token.builder()
                        .clientId("clientId")
                        .secret("secret")
                        .scope("scope")
                        .uri("/token")
                .build());
        ResponseEntity<TokenResponse> responseEntity = ResponseEntity.ok(expected);
        when(harpRestTemplate.exchange(anyString(), any(), any(), eq(TokenResponse.class)))
                .thenReturn(responseEntity);
        // when
        TokenResponse actual = harpProxyService.getToken();
        // then
        assertThat(actual.getAccessToken(), is("token"));
    }

    @Test
    void fetchUserDetailsReturnsUserDetailsResponse() {
        // given
        List<String> harpIds = List.of("id1", "id2");
        String token = "accessToken";
        when(harpConfig.getUserFind()).thenReturn(HarpConfig.UserFind.builder()
                .uri("/userFindApi").build());
        UserDetailsResponse expected = new UserDetailsResponse();
        when(harpRestTemplate.postForObject(anyString(), any(), eq(UserDetailsResponse.class)))
                .thenReturn(expected);
        // when
        UserDetailsResponse actual = harpProxyService.fetchUserDetails(harpIds, token);
        // then
        assertThat(actual, is(expected));
    }

    @Test
    void fetchUserRolesReturnsUserRolesResponse() {
        // given
        String harpId = "id1";
        String token = "accessToken";
        when(harpConfig.getUserRoles()).thenReturn(HarpConfig.UserRoles.builder()
                        .uri("/userRoleCreationApi")
                .build());
        UserRolesResponse expected = UserRolesResponse.builder().success(true).build();
        when(harpRestTemplate.postForObject(anyString(), any(), eq(UserRolesResponse.class)))
                .thenReturn(expected);
        // when
        UserRolesResponse actual = harpProxyService.fetchUserRoles(harpId, token);
        // then
        assertThat(actual, is(expected));
    }
}
