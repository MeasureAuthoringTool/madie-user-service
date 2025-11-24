package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdatesJobResultDto {
  List<String> updatedHarpIds = new ArrayList<>();
  List<String> failedHarpIds = new ArrayList<>();
}
