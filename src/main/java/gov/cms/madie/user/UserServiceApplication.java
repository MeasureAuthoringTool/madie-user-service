package gov.cms.madie.user;

import gov.cms.madie.user.services.LogInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableScheduling
@SpringBootApplication
public class UserServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserServiceApplication.class, args);
  }

  @Bean
  public WebMvcConfigurer corsConfigurer(@Autowired LogInterceptor logInterceptor) {
    return new WebMvcConfigurer() {

      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        WebMvcConfigurer.super.addInterceptors(registry);
        registry.addInterceptor(logInterceptor);
      }

      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOrigins(
                "http://localhost:9000",
                "https://dev-madie.hcqis.org",
                "https://test-madie.hcqis.org",
                "https://impl-madie.hcqis.org",
                "https://madie.cms.gov")
            .allowedMethods("GET", "POST", "PUT", "DELETE");
      }
    };
  }
}
