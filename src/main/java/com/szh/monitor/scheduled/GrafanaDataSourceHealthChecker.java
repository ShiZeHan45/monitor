package com.szh.monitor.scheduled;

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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GrafanaDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaDataSourceHealthChecker.class);

    @Autowired
    private GrafanaDataSourceService dataSourceService;

    private final HttpClient httpClient;

    public GrafanaDataSourceHealthChecker() {
        httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
                );
    }

    @Scheduled(fixedRate = 300_000)
    public void checkHealth() {
        List<GrafanaDataSource> dataSources = dataSourceService.listEnabled();
        for (GrafanaDataSource ds : dataSources) {
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(GrafanaDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        boolean isOnline = false;
        try {
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
            
            Map<String, Object> response = webClient.get()
                    .uri(baseUrl + "/api/org")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(WebClientResponseException.class, e -> {
                        logger.warn("数据源 [{}] 访问失败: {} - {}", environmentName, e.getStatusCode(), e.getMessage());
                        return Mono.empty();
                    })
                    .onErrorResume(Exception.class, e -> {
                        logger.warn("数据源 [{}] 访问异常: {}", environmentName, e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            isOnline = response != null && !response.isEmpty();
            if (isOnline) {
                logger.debug("数据源 [{}] 健康检查通过", environmentName);
            }
            dataSourceService.updateOnlineStatus(ds.getId(), isOnline);

        } catch (Exception e) {
            logger.error("数据源 [{}] 健康检查异常: {}", environmentName, e.getMessage());
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }
}