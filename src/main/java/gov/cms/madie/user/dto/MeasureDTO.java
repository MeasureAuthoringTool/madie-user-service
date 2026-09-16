package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeasureDTO {

  private String measureName;
  private String version;
  private String model;
  private Instant lastModifiedAt;
  private String ownerDisplayName;
  private MeasureMetaDataDTO measureMetaData;
  private MeasureSetDTO measureSet;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MeasureMetaDataDTO {
    private boolean draft;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MeasureSetDTO {
    private Integer cmsId;
    private String owner;
  }
}
