package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body sent to the excel-export service's {@code POST /excel/user-export} endpoint. Matches
 * the downstream {@code GenerateUserExportDto} contract: {@code { "rows": [...] }}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExportRequest {
  private List<UserExportRow> rows;
}
