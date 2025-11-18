package gov.cms.madie.user;

import gov.cms.madie.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class UserServiceApplicationTests {
    @Autowired
    private UserService userService;

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
}
