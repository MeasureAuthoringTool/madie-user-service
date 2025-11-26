package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarpErrorResponse {
  private String errorCode;
  private String errorSummary;
  private String errorMessage;
  private String details;
}
