package gov.cms.madie.user.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
@ConfigurationProperties(prefix = "harp")
@Data
public class HarpConfig {

  private String baseUrl;
  private String programName;
  private String adoName;
  private Token token;
  private UserRoles userRoles;
  private UserFind userFind;

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  public static class Token {
    private String uri;
    private String scope;
    private String clientId;
    private String secret;
  }

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  public static class UserRoles {
    private String uri;
  }

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  public static class UserFind {
    private String uri;
  }

  @Profile("!test")
  @Bean(name = "harpRestTemplate")
  public RestTemplate harpRestTemplate(RestTemplateBuilder builder) {
    RestTemplate restTemplate = builder.build();
    restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(baseUrl));
    return restTemplate;
  }
}
