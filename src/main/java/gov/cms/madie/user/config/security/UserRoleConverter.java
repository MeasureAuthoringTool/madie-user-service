package gov.cms.madie.user.config.security;

import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.user.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserRoleConverter implements Converter<Jwt, JwtAuthenticationToken> {
  private final UserService userService;

  @Autowired
  public UserRoleConverter(UserService userService) {
    this.userService = userService;
  }

  @Override
  public JwtAuthenticationToken convert(Jwt jwt) {
    String userId = jwt.getSubject().toLowerCase();
    UserRolesDto userRolesDto = userService.getUserRoles(userId);
    if (userRolesDto == null) {
      log.warn("No User found for user harp ID: {}", userId);
      return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }
    Collection<String> roles = userRolesDto.getRoles();
    if (CollectionUtils.isEmpty(roles)) {
      log.warn("No roles found for user: {}", userId);
      return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }

    // Convert roles to SimpleGrantedAuthority with "ROLE_" prefix
    List<GrantedAuthority> authorities =
        roles.stream()
            .map(role -> "ROLE_" + role.toUpperCase())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

    log.info("User {} has roles: {}", userId, roles);
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
