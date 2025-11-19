package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponse {
  private boolean success;
  private List<UserRole> userRoles;
  private int totalRoleCount;
  private String message;
}
