package com.szh.monitor.scheduled;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.service.GrafanaDataSourceService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GrafanaDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaDataSourceHealthChecker.class);

    @Autowired
    private GrafanaDataSourceService dataSourceService;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GrafanaDataSourceHealthChecker() {
        httpClient = HttpClient.create(reactor.netty.resources.ConnectionProvider.builder("health-check-pool")
                        .maxConnections(10)
                        .pendingAcquireTimeout(Duration.ofSeconds(30))
                        .maxIdleTime(Duration.ofSeconds(60))
                        .maxLifeTime(Duration.ofMinutes(5))
                        .build())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .keepAlive(true)
                .tcpConfiguration(tcp -> tcp
                        .bootstrap(bootstrap -> bootstrap.option(ChannelOption.SO_KEEPALIVE, true))
                )
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
                );
    }

    @Scheduled(fixedRate = 300_000)
    public void checkHealth() {
        List<GrafanaDataSource> dataSources = dataSourceService.listEnabled();
        int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        LocalTime now = LocalTime.now();
        for (GrafanaDataSource ds : dataSources) {
            // 星期检查
            if (ds.getWeek() != null && !ds.getWeek().isEmpty()) {
                try {
                    List<Integer> week = objectMapper.readValue(ds.getWeek(), new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() {});
                    if (!week.contains(dayOfWeek)) {
                        logger.debug("数据源 [{}] 不在配置的执行星期内，跳过健康检查", ds.getEnvironmentName());
                        continue;
                    }
                } catch (Exception e) {
                    logger.warn("解析数据源 [{}] 的星期配置失败", ds.getEnvironmentName());
                }
            }
            // 时间段检查
            if (ds.getStartTime() != null && !ds.getStartTime().isEmpty() && ds.getEndTime() != null && !ds.getEndTime().isEmpty()) {
                try {
                    LocalTime start = LocalTime.parse(ds.getStartTime());
                    LocalTime end = LocalTime.parse(ds.getEndTime());
                    if (now.isBefore(start) || now.isAfter(end)) {
                        logger.debug("数据源 [{}] 不在配置的执行时间段内，跳过健康检查", ds.getEnvironmentName());
                        continue;
                    }
                } catch (Exception e) {
                    logger.warn("解析数据源 [{}] 的时间段配置失败", ds.getEnvironmentName());
                }
            }
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(GrafanaDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        boolean isOnline = false;
        
        final int maxRetries = 2;
        final long retryDelayMs = 1000;
        
        String basicAuth = Base64Utils.encodeToString(
                (ds.getUsername() + ":" + ds.getPassword()).getBytes()
        );

        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        String baseUrl = ds.getUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
            final int currentRetry = retryCount;
            try {
                Map<String, Object> response = webClient.get()
                        .uri(baseUrl + "/api/org")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .onErrorResume(WebClientResponseException.class, e -> {
                            if (currentRetry < maxRetries) {
                                logger.warn("数据源 [{}] 访问失败 ({}次重试): {} - {}", 
                                        environmentName, currentRetry + 1, e.getStatusCode(), e.getMessage());
                            } else {
                                logger.warn("数据源 [{}] 访问失败 (已达最大重试次数): {} - {}", 
                                        environmentName, e.getStatusCode(), e.getMessage());
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(Exception.class, e -> {
                            if (currentRetry < maxRetries) {
                                logger.warn("数据源 [{}] 访问异常 ({}次重试): {}", 
                                        environmentName, currentRetry + 1, e.getMessage());
                            } else {
                                logger.warn("数据源 [{}] 访问异常 (已达最大重试次数): {}", 
                                        environmentName, e.getMessage());
                            }
                            return Mono.empty();
                        })
                        .block();

                isOnline = response != null && !response.isEmpty();
                if (isOnline) {
                    logger.debug("数据源 [{}] 健康检查通过{}", environmentName, retryCount > 0 ? " (重试" + retryCount + "次后)" : "");
                    break;
                }

            } catch (Exception e) {
                if (retryCount < maxRetries) {
                    logger.warn("数据源 [{}] 健康检查异常 ({}次重试): {}", 
                            environmentName, retryCount + 1, e.getMessage());
                } else {
                    logger.error("数据源 [{}] 健康检查异常 (已达最大重试次数): {}", 
                            environmentName, e.getMessage());
                }
            }

            if (!isOnline && retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        dataSourceService.updateOnlineStatus(ds.getId(), isOnline);
    }
}