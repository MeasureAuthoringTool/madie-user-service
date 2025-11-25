package gov.cms.madie.user.services;

import gov.cms.madie.user.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenManager {

  private final HarpProxyService harpProxyService;
  private static TokenResponse currentToken;

  public TokenManager(HarpProxyService harpProxyService) {
    this.harpProxyService = harpProxyService;
    forceRefreshToken();
  }

  public synchronized TokenResponse getCurrentToken() {
    if (currentToken == null || currentToken.isExpired()) {
      try {
        currentToken = harpProxyService.getToken();
      } catch (Exception e) {
        log.error("Unable to refresh HARP token due to error", e);
        currentToken = null;
      }
    }
    return currentToken;
  }

  /** This method will be called every 20 minutes to refresh the HARP token. */
  @Scheduled(cron = "0 0,20,40 * * * *")
  public synchronized void forceRefreshToken() {
    try {
      currentToken = harpProxyService.getToken();
      log.info(
          "HARP token refresh was triggered. New token expires at: {}",
          currentToken.getExpiresAt());
    } catch (Exception e) {
      log.error("Unable to refresh HARP token due to error", e);
      currentToken = null;
    }
  }
}
