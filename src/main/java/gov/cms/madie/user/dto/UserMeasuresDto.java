package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single user's owned and shared measures returned by the measure-service bulk export endpoint
 * ({@code PUT /admin/measures/bulk-export}). Each list holds the latest measure per family.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMeasuresDto {
  private List<MeasureDTO> ownedMeasures = new ArrayList<>();
  private List<MeasureDTO> sharedMeasures = new ArrayList<>();
}
