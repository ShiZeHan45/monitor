package com.szh.monitor.service.impl;

import com.szh.monitor.config.MonitorRules;
import com.szh.monitor.config.WatcherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrafanaLogServiceImp {
    Logger logger = LoggerFactory.getLogger(GrafanaLogServiceImp.class);
    private final WatcherConfig watcherConfig;
    private final MonitorRules monitorListConfig;
    private final WebClient webClient;

    private final SendDispatchService sendDispatchService;
    // 每个监控项独立记住上次处理的时间戳
    private final Map<String, Long> lastTsMap = new HashMap<>();


    public GrafanaLogServiceImp(WatcherConfig watcherConfig, MonitorRules monitorListConfig,SendDispatchService sendDispatchService) {
        this.watcherConfig = watcherConfig;
        this.monitorListConfig = monitorListConfig;
        this.sendDispatchService = sendDispatchService;


        String basicAuth = Base64Utils.encodeToString(
                (watcherConfig.getGrafana().getPrimary().getUsername() + ":" + watcherConfig.getGrafana().getPrimary().getPassword()).getBytes()
        );


        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }


    @Scheduled(fixedRate = 30000)
    public void runMonitor() {
        for (MonitorRules.Rule item : monitorListConfig.getList()) {

            if (!item.isEnabled()) continue;

            try {
                processMonitor(item);
            } catch (Exception e) {
                logger.error("Monitor {} error", item.getName(), e);
            }
        }
    }


    private void processMonitor(MonitorRules.Rule item) {
        long now = Instant.now().toEpochMilli() * 1_000_000;
        long start = lastTsMap.getOrDefault(item.getName(),now - 2 * 60 * 1_000_000_000L);
        LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start / 1_000_000), ZoneId.systemDefault());
        LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now / 1_000_000), ZoneId.systemDefault());

        logger.debug("{} 查询时间区间 {} ~ {} 产生的日志 ",item.getName(),startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        String baseUrl = watcherConfig.getGrafana().getPrimary().getUrl();
        String dsId = watcherConfig.getGrafana().getPrimary().getDatasourceId();


        String url = baseUrl + "/api/datasources/proxy/" + dsId + "/loki/api/v1/query_range";


        webClient.get()
                .uri(url + "?query={query}&start={start}&end={end}&limit={limit}",
                        item.getQueryExpr(), start, now, 200)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> handleResult(watcherConfig.getGrafana().getPrimary().getEnvironmentName(),item, body))
                .onErrorResume(e -> {
                    logger.error("❌ WebClient 调用 Loki 失败", e);
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<Void> handleResult(String environmentName,MonitorRules.Rule item, Map body) {
        if (body == null) return Mono.empty();


        Map data = (Map) body.get("data");
        if (data == null) return Mono.empty();


        List result = (List) data.get("result");
        if (result == null) return Mono.empty();


        long lastTs = lastTsMap.getOrDefault(item.getName(), 0L);


        List<String> hitLogs = new ArrayList<>();


        for (Object obj : result) {
            Map stream = (Map) obj;
            List<List> values = (List<List>) stream.get("values");
            if (values == null) continue;
            if (values != null && !values.isEmpty()) {
                values.sort(Comparator.comparing(v -> Long.parseLong((String) v.get(0))));
            }

// values: [ [timestamp, log], ... ]
            for (int i = 0; i < values.size();  i++) {
                List entry = values.get(i);
                long ts = Long.parseLong((String) entry.get(0));
                String log = (String) entry.get(1);

                if (ts <= lastTs) {
                    continue;
                }
                //匹配上关键词 同时匹配不上移除关键词
                if (item.getKeywords().stream().anyMatch(log::contains)&&item.getExclusionKeywords().stream().noneMatch(log::contains)) {
                    int end = Math.min(i + item.getContextLines(), values.size());
                    List<String> context = values.subList(i, end).stream()
                            .map(v -> (String) v.get(1))
                            .collect(Collectors.toList());
                    hitLogs.add(String.join("\n", context));
                    break;
                }
            }
        }


// 无命中
        if (hitLogs.isEmpty()) return Mono.empty();


// 更新 lastTs
        long maxTs = lastTs;
        for (Object rObj : result) {
            Map rMap = (Map) rObj;
            List<List> vals = (List<List>) rMap.get("values");
            if (vals == null) continue;
            for (Object vObj : vals) {
                List v = (List) vObj;
                long t = Long.parseLong((String) v.get(0));
                if (t > maxTs) maxTs = t;
            }
        }
        lastTsMap.put(item.getName(), maxTs);

// 聚合推送
        String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```",environmentName,hitLogs.stream().collect(Collectors.joining("")));
        sendDispatchService.sendSimpleMarkDownMsg(content);
        logger.info("📩 已推送 {} 条日志，并更新 lastTs={},时间：{} 推送内容：{}", hitLogs.size(), maxTs,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs/1_000_000), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),content);
        return Mono.empty();
    }
}
