package gov.cms.madie.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
    // The template must use the pooled Apache HttpClient factory, not the JDK default.
    assertThat(
        restTemplate.getRequestFactory(),
        is(instanceOf(HttpComponentsClientHttpRequestFactory.class)));
  }

  @Test
  void hasSensibleConnectionPoolDefaults() {
    MeasureServiceConfig config = new MeasureServiceConfig();

    assertThat(config.getMaxConnections(), is(20));
    assertThat(config.getConnectTimeoutMillis(), is(5_000L));
    assertThat(config.getResponseTimeoutMillis(), is(60_000L));
  }

  @Test
  void buildsRestTemplateWithCustomPoolAndTimeouts() {
    MeasureServiceConfig config = new MeasureServiceConfig();
    config.setBaseUrl("http://measure:8080/api");
    config.setMaxConnections(50);
    config.setConnectTimeoutMillis(1_000L);
    config.setResponseTimeoutMillis(3_000L);

    assertThat(config.getMaxConnections(), is(50));
    assertThat(config.getConnectTimeoutMillis(), is(1_000L));
    assertThat(config.getResponseTimeoutMillis(), is(3_000L));

    RestTemplate restTemplate = config.measureServiceRestTemplate(new RestTemplateBuilder());
    assertThat(restTemplate, notNullValue());
    assertThat(
        restTemplate.getRequestFactory(),
        is(instanceOf(HttpComponentsClientHttpRequestFactory.class)));
  }
}
