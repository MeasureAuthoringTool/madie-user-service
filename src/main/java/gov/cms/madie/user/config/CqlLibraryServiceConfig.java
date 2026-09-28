package gov.cms.madie.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the downstream cql-library-service. Reads the base URL from configuration (see
 * application.yaml: {@code cql-library-service.base-url}).
 */
@Configuration
@ConfigurationProperties(prefix = "cql-library-service")
@Data
public class CqlLibraryServiceConfig {

  private String baseUrl;

  @Bean(name = "cqlLibraryServiceRestTemplate")
  public RestTemplate cqlLibraryServiceRestTemplate(RestTemplateBuilder builder) {
    return builder.requestFactory(HttpComponentsClientHttpRequestFactory::new).build();
  }
}
