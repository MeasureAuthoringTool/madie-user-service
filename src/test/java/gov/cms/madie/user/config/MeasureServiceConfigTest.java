package gov.cms.madie.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MeasureServiceConfigTest {

  @Test
  void holdsBaseUrlAndBuildsRestTemplate() {
    MeasureServiceConfig config = new MeasureServiceConfig();
    config.setBaseUrl("http://measure:8080/api");

    assertThat(config.getBaseUrl(), is("http://measure:8080/api"));

    RestTemplate restTemplate = config.measureServiceRestTemplate(new RestTemplateBuilder());
    assertThat(restTemplate, notNullValue());
  }
}
