package gov.cms.madie.user.config.security;

import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.user.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleConverterTest {

  @Mock private UserService userService;
  @InjectMocks private UserRoleConverter userRoleConverter;

  private Jwt buildJwt(String subject) {
    return Jwt.withTokenValue("fake.jwt.token")
        .header("alg", "RS256")
        .subject(subject)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  @Test
  void convertReturnsAuthoritiesWhenUserHasSingleRole() {
    Jwt jwt = buildJwt("TestUser");
    when(userService.getUserRoles("testuser"))
        .thenReturn(new UserRolesDto("testuser", List.of("MADiE-User")));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), hasSize(1));
    assertThat(
        token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
        contains("ROLE_MADIE-USER"));
    verify(userService).getUserRoles("testuser");
  }

  @Test
  void convertReturnsMultipleAuthoritiesWhenUserHasMultipleRoles() {
    Jwt jwt = buildJwt("AdminUser");
    when(userService.getUserRoles("adminuser"))
        .thenReturn(new UserRolesDto("adminuser", List.of("MADiE-User", "MADiE-Admin")));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), hasSize(2));
    assertThat(
        token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
        containsInAnyOrder("ROLE_MADIE-USER", "ROLE_MADIE-ADMIN"));
  }

  @Test
  void convertReturnsEmptyAuthoritiesWhenUserNotFound() {
    Jwt jwt = buildJwt("UnknownUser");
    when(userService.getUserRoles("unknownuser")).thenReturn(null);

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), is(empty()));
    verify(userService).getUserRoles("unknownuser");
  }

  @Test
  void convertReturnsEmptyAuthoritiesWhenUserHasEmptyRoles() {
    Jwt jwt = buildJwt("EmptyRolesUser");
    when(userService.getUserRoles("emptyrolesuser"))
        .thenReturn(new UserRolesDto("emptyrolesuser", List.of()));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), is(empty()));
  }

  @Test
  void convertReturnsEmptyAuthoritiesWhenRolesListIsNull() {
    Jwt jwt = buildJwt("NullRolesUser");
    when(userService.getUserRoles("nullrolesuser"))
        .thenReturn(new UserRolesDto("nullrolesuser", null));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), is(empty()));
  }

  @Test
  void convertLowercasesSubjectBeforeLookup() {
    Jwt jwt = buildJwt("UPPERCASE-USER");
    when(userService.getUserRoles("uppercase-user"))
        .thenReturn(new UserRolesDto("uppercase-user", List.of("MADiE-User")));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(token.getAuthorities(), hasSize(1));
    // Verify that the service was called with the lowercased subject
    verify(userService).getUserRoles("uppercase-user");
  }

  @Test
  void convertUppercasesRoleNamesInAuthorities() {
    Jwt jwt = buildJwt("user1");
    when(userService.getUserRoles("user1"))
        .thenReturn(new UserRolesDto("user1", List.of("madie-user")));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token, is(notNullValue()));
    assertThat(
        token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
        contains("ROLE_MADIE-USER"));
  }

  @Test
  void convertPreservesOriginalJwtInToken() {
    Jwt jwt = buildJwt("user1");
    when(userService.getUserRoles("user1"))
        .thenReturn(new UserRolesDto("user1", List.of("MADiE-User")));

    JwtAuthenticationToken token = userRoleConverter.convert(jwt);

    assertThat(token.getToken(), is(sameInstance(jwt)));
  }
}
