package com.szh.monitor.scheduled;

import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.impl.GrafanaLogServiceImp;
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
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GrafanaDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaDataSourceHealthChecker.class);

    @Autowired
    private GrafanaDataSourceService dataSourceService;

    @Autowired
    private GrafanaLogServiceImp grafanaLogService;

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

    @Scheduled(fixedRate = 60_000)
    public void checkHealth() {
        List<GrafanaDataSource> dataSources = dataSourceService.listEnabled();
        for (GrafanaDataSource ds : dataSources) {
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(GrafanaDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        try {
            String basicAuth = Base64Utils.encodeToString(
                    (ds.getUsername() + ":" + ds.getPassword()).getBytes()
            );

            WebClient webClient = WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            Map<String, Object> response = webClient.get()
                    .uri(ds.getUrl() + "/api/datasources/proxy/" + ds.getDatasourceId() + "/api/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> {
                        logger.warn("数据源 [{}] 健康检查失败: {}", environmentName, e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            boolean isOnline = response != null && !response.isEmpty();
            dataSourceService.updateOnlineStatus(ds.getId(), isOnline);

            if (isOnline) {
                logger.info("数据源 [{}] 在线", environmentName);
            } else {
                logger.warn("数据源 [{}] 离线", environmentName);
            }

        } catch (Exception e) {
            logger.error("数据源 [{}] 健康检查异常: {}", environmentName, e.getMessage());
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }
}
