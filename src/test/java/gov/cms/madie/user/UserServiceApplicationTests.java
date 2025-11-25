package gov.cms.madie.user;

import gov.cms.madie.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceApplicationTests {
  @Autowired private UserService userService;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {
    // given
    // nothing to set up
    // when
    // nothing to call
    // then
    // context loads if no exception is thrown
  }

  @Test
  void userServiceBeanIsLoaded() {
    // given
    // nothing to set up
    // when
    // nothing to call
    // then
    assertThat(userService, is(notNullValue()));
  }

  @Test
  void webMvcConfigurerBeanIsLoaded() {
    String[] beanNames = applicationContext.getBeanNamesForType(WebMvcConfigurer.class);
    assertThat(beanNames.length, is(notNullValue()));
    assertThat(beanNames.length > 0, is(true));
    for (String beanName : beanNames) {
      Object bean = applicationContext.getBean(beanName);
      assertThat(bean, is(notNullValue()));
    }
  }

  @Test
  void corsHeadersArePresentForAllowedOrigin() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/").header("Origin", "http://localhost:9000"))
        .andExpect(
            MockMvcResultMatchers.header()
                .string("Access-Control-Allow-Origin", "http://localhost:9000"));
  }

  @Test
  void corsHeadersAreNotPresentForDisallowedOrigin() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/").header("Origin", "http://notallowed.com"))
        .andExpect(MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
