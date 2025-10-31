package gov.cms.madie.user.dto;

import gov.cms.madie.models.access.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserActivityDto {
  private String harpId;
  private String firstName;
  private String lastName;
  private String email;
  private Instant lastLoginAt;
  private Instant accessStartAt;
  private UserStatus status;
}
