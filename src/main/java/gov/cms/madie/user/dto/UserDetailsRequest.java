package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsRequest {
  private String programName;
  private Map<String, List<String>> attributes;
  private String details;
  private int offset;
  private int max;
}
