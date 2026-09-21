package gov.cms.madie.user.config;

import lombok.Data;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the downstream measure-service. Reads the base URL from configuration (see
 * application.yaml: {@code measure-service.base-url}).
 *
 * <p>The {@link RestTemplate} is backed by a pooled Apache HttpClient 5 connection manager. The
 * pool size must be at least as large as {@code user-export.concurrency} so that the parallel Full
 * User Export fan-out is not throttled by the default per-route connection limit.
 */
@Configuration
@ConfigurationProperties(prefix = "measure-service")
@Data
public class MeasureServiceConfig {

  private String baseUrl;

  /** Maximum connections (total and per-route). Should be {@code >= user-export.concurrency}. */
  private int maxConnections = 20;

  /** Timeout for establishing a TCP connection, in milliseconds. */
  private long connectTimeoutMillis = 5_000;

  /** Timeout for receiving a response after the request is sent, in milliseconds. */
  private long responseTimeoutMillis = 60_000;

  @Bean(name = "measureServiceRestTemplate")
  public RestTemplate measureServiceRestTemplate(RestTemplateBuilder builder) {
    ConnectionConfig connectionConfig =
        ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis))
            .build();

    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(maxConnections)
            .setMaxConnPerRoute(maxConnections)
            .setDefaultConnectionConfig(connectionConfig)
            .build();

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMillis))
            .build();

    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);
    return builder.requestFactory(() -> requestFactory).build();
  }
}
