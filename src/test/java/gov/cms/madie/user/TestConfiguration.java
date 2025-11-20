package gov.cms.madie.user;

import gov.cms.madie.user.dto.TokenResponse;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Profile("test")
@Configuration
public class TestConfiguration {

  @Bean
  public RestTemplate harpRestTemplate() {
    RestTemplate harpRestTemplate = Mockito.mock(RestTemplate.class);
    when(harpRestTemplate.exchange(
            eq("/harpAuthzApi/token"),
            eq(org.springframework.http.HttpMethod.POST),
            Mockito.any(),
            eq(TokenResponse.class)))
        .thenReturn(
            new org.springframework.http.ResponseEntity<>(
                TokenResponse.builder().accessToken("fake-token").build(),
                org.springframework.http.HttpStatus.OK));
    return harpRestTemplate;
  }
}
