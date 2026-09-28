package gov.cms.madie.user.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

class CqlLibraryServiceConfigTest {

  @Test
  void holdsBaseUrlAndBuildsRestTemplate() {
    CqlLibraryServiceConfig config = new CqlLibraryServiceConfig();
    config.setBaseUrl("http://cql-library:8082/api");

    assertThat(config.getBaseUrl(), is("http://cql-library:8082/api"));

    RestTemplate restTemplate = config.cqlLibraryServiceRestTemplate(new RestTemplateBuilder());
    assertThat(restTemplate, notNullValue());
    assertThat(
        restTemplate.getRequestFactory(),
        is(instanceOf(HttpComponentsClientHttpRequestFactory.class)));
  }
}
