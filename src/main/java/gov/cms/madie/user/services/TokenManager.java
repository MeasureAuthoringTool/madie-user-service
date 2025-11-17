package gov.cms.madie.user.services;

import gov.cms.madie.user.dto.TokenResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class TokenManager {

  private final HarpProxyService harpProxyService;
  private static TokenResponse currentToken;

  public synchronized TokenResponse getCurrentToken() {
    if (currentToken == null || currentToken.isExpired()) {
      currentToken = harpProxyService.getToken();
    }
    return currentToken;
  }

  /** This method will be called every 20 minutes to refresh the HARP token. */
  @PostConstruct
  @Scheduled(cron = "0 0,20,40 * * * *")
  public synchronized void forceRefreshToken() {
    currentToken = harpProxyService.getToken();
    log.info(
        "HARP token refresh was triggered. New token expires at: {}", currentToken.getExpiresAt());
  }
}
