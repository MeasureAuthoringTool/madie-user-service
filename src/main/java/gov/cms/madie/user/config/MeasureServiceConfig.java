package gov.cms.madie.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the downstream measure-service. Reads the base URL from configuration (see
 * application.yaml: {@code measure-service.base-url}).
 */
@Configuration
@ConfigurationProperties(prefix = "measure-service")
@Data
public class MeasureServiceConfig {

    private String baseUrl;

    @Bean(name = "measureServiceRestTemplate")
    public RestTemplate measureServiceRestTemplate(RestTemplateBuilder builder) {
        return builder.requestFactory(HttpComponentsClientHttpRequestFactory::new).build();
    }
}