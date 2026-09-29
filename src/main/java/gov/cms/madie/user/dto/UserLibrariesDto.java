package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single user's owned and shared libraries returned by the cql-library-service bulk export
 * endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserLibrariesDto {
  private List<LibraryDTO> ownedLibraries = new ArrayList<>();
  private List<LibraryDTO> sharedLibraries = new ArrayList<>();
}
