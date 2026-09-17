package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Optional request body for the Full User Export endpoint.
 *
 * <p>When {@code harpIds} is null/empty the export covers all users; otherwise it is limited to the
 * selected users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExportRequestDto {
  private List<String> harpIds;
}
