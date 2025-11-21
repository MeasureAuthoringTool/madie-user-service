package gov.cms.madie.user.controllers;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.MadieUser;
import gov.cms.madie.user.config.SecurityConfig;
import gov.cms.madie.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserController.class})
@ActiveProfiles("test")
@Import(SecurityConfig.class)
public class UserControllerMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @Test
  @WithMockUser(username = "testuser")
  void getUserByHarpIdSuccessfully() throws Exception {
    // Given
    String harpId = "harper";
    MadieUser user =
        MadieUser.builder()
            .harpId(harpId)
            .firstName("John")
            .lastName("Doe")
            .roles(List.of(HarpRole.builder().role("Admin").roleType("ADMIN").build()))
            .build();
    when(userService.getUserByHarpId(harpId)).thenReturn(user);

    // When & Then
    mockMvc
        .perform(get("/users/" + harpId).with(csrf()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.harpId", is(harpId)))
        .andExpect(jsonPath("$.firstName", is("John")))
        .andExpect(jsonPath("$.lastName", is("Doe")))
        .andExpect(jsonPath("$.roles[0].roleType", is("ADMIN")))
        .andExpect(jsonPath("$.roles[0].role", is("Admin")));

    verify(userService, times(1)).getUserByHarpId(harpId);
  }
}
