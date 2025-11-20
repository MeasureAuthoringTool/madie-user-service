package gov.cms.madie.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("expires_in")
  private Long expiresIn;

  private String scope;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private Instant expiresAt;

  public Instant getExpiresAt() {
    if (expiresAt == null) {
      // Parse JWT to get expiry
      if (accessToken != null && accessToken.split("\\.").length == 3) {
        try {
          String[] parts = accessToken.split("\\.");
          String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
          // Simple JSON parsing for "exp" field
          int expIndex = payload.indexOf("\"exp\":");
          if (expIndex != -1) {
            int start = expIndex + 6;
            int end = payload.indexOf(',', start);
            if (end == -1) {
              end = payload.indexOf('}', start);
            }
            String expStr = payload.substring(start, end).replaceAll("[^0-9]", "");
            long expSeconds = Long.parseLong(expStr);
            expiresAt = Instant.ofEpochSecond(expSeconds);
          }
        } catch (Exception e) {
          // If parsing fails, treat as expired
          return null;
        }
      } else {
        // No valid JWT, treat as expired
        return null;
      }
    }
    return expiresAt;
  }

  /**
   * Checks if the token is expired or will expire within the next 30 seconds.
   *
   * @return true if the token is expired or will expire within the next 30 seconds, false
   *     otherwise.
   */
  public boolean isExpired() {
    Instant now = Instant.now();
    // Check if expiresAt is before now + 30 seconds
    Instant expiresAt = getExpiresAt();
    return expiresAt == null || expiresAt.isBefore(now.plusSeconds(30));
  }
}
