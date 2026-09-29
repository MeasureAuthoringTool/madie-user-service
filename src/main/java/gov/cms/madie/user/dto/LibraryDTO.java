package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal CQL library summary returned by the cql-library-service bulk export endpoint. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LibraryDTO {
  private String cqlLibraryName;
  private String version;
  private String ownerDisplayName;
  private String model;
  private Instant lastModifiedAt;
  private boolean draft;
  private LibrarySetDto librarySet;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LibrarySetDto {
    private String owner;
  }
}
