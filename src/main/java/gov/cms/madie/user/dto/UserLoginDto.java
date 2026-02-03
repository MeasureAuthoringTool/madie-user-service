package gov.cms.madie.user.dto;

import gov.cms.madie.models.access.HarpRole;
import gov.cms.madie.models.access.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDto {
  private String harpId;
  private UserStatus status;
  private List<HarpRole> roles;
}
