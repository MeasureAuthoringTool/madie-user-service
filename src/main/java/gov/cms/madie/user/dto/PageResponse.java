package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Minimal representation of a Spring Data {@code Page} JSON response, capturing only the fields
 * needed to iterate all pages of a downstream search endpoint.
 *
 * @param <T> the content element type
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageResponse<T> {
  private List<T> content;
  private boolean last;
  private int number;
  private int totalPages;
  private long totalElements;
}
