package gov.cms.madie.user.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
class HarpConfigTest {

  @Test
  void harpRestTemplateShouldBeConfiguredWithBaseUrl() {
    // given
    HarpConfig config = new HarpConfig();
    config.setBaseUrl("https://test.tst");
    RestTemplateBuilder builder = new RestTemplateBuilder();

    // when
    RestTemplate restTemplate = config.harpRestTemplate(builder);

    // then
    assertThat(restTemplate, is(notNullValue()));
    assertThat(
        restTemplate.getUriTemplateHandler(), is(instanceOf(DefaultUriBuilderFactory.class)));
    DefaultUriBuilderFactory uriBuilderFactory =
        (DefaultUriBuilderFactory) restTemplate.getUriTemplateHandler();
    assertThat(uriBuilderFactory.expand("/test").toString(), is("https://test.tst/test"));
  }

  @Test
  void tokenConfigShouldHoldAllProperties() {
    // given
    HarpConfig.Token token = new HarpConfig.Token();

    // when
    token.setUri("/oauth/token");
    token.setScope("harp.read");
    token.setClientId("test-client-id");
    token.setSecret("test-secret");

    // then
    assertThat(token.getUri(), is("/oauth/token"));
    assertThat(token.getScope(), is("harp.read"));
    assertThat(token.getClientId(), is("test-client-id"));
    assertThat(token.getSecret(), is("test-secret"));
  }

  @Test
  void userRolesConfigShouldHoldUri() {
    // given
    HarpConfig.UserRoles userRoles = new HarpConfig.UserRoles();

    // when
    userRoles.setUri("/userRolesApi/user/{harpId}/role");

    // then
    assertThat(userRoles.getUri(), is("/userRolesApi/user/{harpId}/role"));
  }

  @Test
  void userFindConfigShouldHoldUri() {
    // given
    HarpConfig.UserFind userFind = new HarpConfig.UserFind();

    // when
    userFind.setUri("/userFindApi/users/{harpId}");

    // then
    assertThat(userFind.getUri(), is("/userFindApi/users/{harpId}"));
  }

  @Test
  void harpConfigShouldHoldAllTopLevelProperties() {
    // given
    HarpConfig config = new HarpConfig();
    HarpConfig.Token token = new HarpConfig.Token();
    HarpConfig.UserRoles userRoles = new HarpConfig.UserRoles();
    HarpConfig.UserFind userFind = new HarpConfig.UserFind();

    // when
    config.setBaseUrl("https://test.tst");
    config.setProgramName("CMS HARP");
    config.setAdoName("MADIE");
    config.setToken(token);
    config.setUserRoles(userRoles);
    config.setUserFind(userFind);

    // then
    assertThat(config.getBaseUrl(), is("https://test.tst"));
    assertThat(config.getProgramName(), is("CMS HARP"));
    assertThat(config.getAdoName(), is("MADIE"));
    assertThat(config.getToken(), is(token));
    assertThat(config.getUserRoles(), is(userRoles));
    assertThat(config.getUserFind(), is(userFind));
  }

  @Test
  void harpConfigShouldSupportCompleteConfiguration() {
    // given
    HarpConfig config = new HarpConfig();
    HarpConfig.Token token = new HarpConfig.Token();
    token.setUri("/oauth/token");
    token.setScope("harp.read");
    token.setClientId("client-123");
    token.setSecret("secret-456");

    HarpConfig.UserRoles userRoles = new HarpConfig.UserRoles();
    userRoles.setUri("/userRolesApi/user/{harpId}/role");

    HarpConfig.UserFind userFind = new HarpConfig.UserFind();
    userFind.setUri("/userFindApi/users/{harpId}");

    // when
    config.setBaseUrl("https://test.tst");
    config.setProgramName("CMS HARP");
    config.setAdoName("MADIE");
    config.setToken(token);
    config.setUserRoles(userRoles);
    config.setUserFind(userFind);

    // then
    assertThat(config.getBaseUrl(), is("https://test.tst"));
    assertThat(config.getProgramName(), is("CMS HARP"));
    assertThat(config.getAdoName(), is("MADIE"));
    assertThat(config.getToken().getUri(), is("/oauth/token"));
    assertThat(config.getToken().getScope(), is("harp.read"));
    assertThat(config.getToken().getClientId(), is("client-123"));
    assertThat(config.getToken().getSecret(), is("secret-456"));
    assertThat(config.getUserRoles().getUri(), is("/userRolesApi/user/{harpId}/role"));
    assertThat(config.getUserFind().getUri(), is("/userFindApi/users/{harpId}"));
  }

  @Test
  void harpRestTemplateShouldHandleRelativeUris() {
    // given
    HarpConfig config = new HarpConfig();
    config.setBaseUrl("https://api.example.com");
    RestTemplateBuilder builder = new RestTemplateBuilder();

    // when
    RestTemplate restTemplate = config.harpRestTemplate(builder);
    DefaultUriBuilderFactory uriBuilderFactory =
        (DefaultUriBuilderFactory) restTemplate.getUriTemplateHandler();

    // then
    assertThat(
        uriBuilderFactory.expand("/path/to/resource").toString(),
        is("https://api.example.com/path/to/resource"));
    assertThat(
        uriBuilderFactory.expand("/another/path").toString(),
        is("https://api.example.com/another/path"));
  }
}
