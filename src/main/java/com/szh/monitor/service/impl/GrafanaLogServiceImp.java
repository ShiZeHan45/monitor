package com.szh.monitor.service.impl;

import com.szh.monitor.config.GrafanaConfig;
import com.szh.monitor.config.MonitorRules;
import com.szh.monitor.service.LogCollectTimeInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.sql.Time;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrafanaLogServiceImp {
    Logger logger = LoggerFactory.getLogger(GrafanaLogServiceImp.class);
    private final Map<String, WebClient> webClientMap = new HashMap<>();
    private final Map<String, GrafanaConfig.GrafanaInfo> grafanaInfoMap = new HashMap<>();
    private final SendDispatchService sendDispatchService;
    private final LogCollectTimeInfoService logCollectTimeInfoService;
    private final Integer TIME = 60;
    // 每个监控项独立记住上次处理的时间戳
    private final Map<String, Long> lastTsMap = new HashMap<>();

    public Map<String, GrafanaConfig.GrafanaInfo> getGrafanaInfoMap(){
        return grafanaInfoMap;
    }

    public void initLastTsMap(String key,Long lastTs){
        lastTsMap.put(key,lastTs);
    }

    public GrafanaLogServiceImp(GrafanaConfig grafanaConfig, SendDispatchService sendDispatchService,LogCollectTimeInfoService logCollectTimeInfoService) {
        this.sendDispatchService = sendDispatchService;
        this.logCollectTimeInfoService = logCollectTimeInfoService;
        for (GrafanaConfig.GrafanaInfo grafanaInfo : grafanaConfig.getList()) {
            String basicAuth = Base64Utils.encodeToString(
                    (grafanaInfo.getUsername() + ":" + grafanaInfo.getPassword()).getBytes()
            );
            webClientMap.put(grafanaInfo.getEnvironmentName(),WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(config -> config.defaultCodecs().maxInMemorySize(30 * 1024 * 1024 )) // 30MB
                    .build());
            grafanaInfoMap.put(grafanaInfo.getEnvironmentName(),grafanaInfo);
        }
        logger.info("webClient初始化完成 {}",webClientMap.keySet());

    }
    @Scheduled(initialDelay = 10_000, fixedRate = 30_000)
    public void supplement() {
        webClientMap.forEach((environmentName, webClient) -> {
            GrafanaConfig.GrafanaInfo grafanaInfo = grafanaInfoMap.get(environmentName);
            for (MonitorRules item : grafanaInfo.getMonitors()) {
                if (!item.isEnabled()) {
                    continue;
                }

                if(grafanaInfo.getStartTime()!=null&&(LocalTime.now().isBefore(grafanaInfo.getStartTime())||LocalTime.now().isAfter(grafanaInfo.getEndTime()))){
                    continue;
                }
                try {
                    processMonitor(item,webClient,grafanaInfo);
                } catch (Exception e) {
                    logger.error("Monitor {} error", item.getName(), e);
                }
            }

        });

    }


    private void processMonitor(MonitorRules item,WebClient webClient,GrafanaConfig.GrafanaInfo grafanaInfo) {
        long now = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli();
        long start = lastTsMap.getOrDefault(grafanaInfo.getEnvironmentName()+"_"+item.getName(),LocalDateTime.now().minusMinutes(TIME)
                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli() );
        LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault());
        LocalDateTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
        if(startTime.plusMinutes(TIME).isBefore(endTime)){//这一步是为了补扫描 监控程序重启或者停止扫描期间产生的日志
            // 开始时间加2分钟如果大于结束时间  ，结束时间就用当前时间，反之 结束时间等于开始时间加2分钟
            endTime = startTime.plusMinutes(TIME);
        }

//        logger.debug("{}-{} 查询时间区间 {} ~ {} 产生的日志 ",grafanaInfo.getEnvironmentName(),item.getName(),startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        String baseUrl = grafanaInfo.getUrl();
        String dsId = grafanaInfo.getDatasourceId();


        String url = baseUrl + "/api/datasources/proxy/" + dsId + "/loki/api/v1/query_range";


        LocalDateTime finalEndTime = endTime;
        webClient.get()
                .uri(url + "?query={query}&start={start}&end={end}&limit={limit}",
                        item.getQueryExpr(), start*1_000_000, finalEndTime
                                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                                .toInstant()
                                .toEpochMilli()*1_000_000, 5000)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> handleResult(grafanaInfo.getEnvironmentName(),item, body,finalEndTime,startTime))
                .onErrorResume(e -> {
                    logger.error("{}-{} ❌ WebClient 调用 Loki 失败",grafanaInfo.getEnvironmentName(),item.getName(), e);
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<Void> handleResult(String environmentName,MonitorRules item, Map body,LocalDateTime endTime,LocalDateTime startTime) {
        if (body == null) {
            logger.debug("{}该时间区间无日志记录",environmentName);
            return Mono.empty();
        }


        Map data = (Map) body.get("data");
        if (data == null) {
            logger.debug("{}该时间区间无日志记录",environmentName);
            return Mono.empty();
        }


        List result = (List) data.get("result");
        if (result == null) {
            logger.debug("{}该时间区间无日志记录",environmentName);
            return Mono.empty();
        }
        boolean flag = startTime.plusMinutes(TIME).isBefore(endTime);
        int count = 0;
        for (Object obj : result) {
            Map stream = (Map) obj;
            List<List> values = (List<List>) stream.get("values");
            if (values == null) continue;
            count=count+values.size();
        }
        logger.debug("{}-{}获取到时间范围[{}~{}]共{}条日志,开始分析匹配关键词：{}",environmentName,item.getName(),
                startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),count,item.getKeywords());

        long lastTs = lastTsMap.getOrDefault(environmentName+"_"+item.getName(), startTime
                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli() );

        List<String> hitLogs = new ArrayList<>();

// 更新 lastTs
        long maxTs = lastTs;
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
                long ts = Long.parseLong((String) entry.get(0))/1_000_000;
                String log = (String) entry.get(1);
                if (ts > maxTs && !flag) maxTs = ts;
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
        lastTsMap.put(environmentName+"_"+item.getName(), maxTs);
        logCollectTimeInfoService.updateOrSave(environmentName,item.getName(),maxTs);
// 无命中
        if (hitLogs.isEmpty()) return Mono.empty();




// 聚合推送
        String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```",environmentName,hitLogs.stream().collect(Collectors.joining("")));
        if (content.length() > 1500) {
            logger.info("推送内容超长，截取1500字符");
            content = content.substring(0, 1500);
        }
        sendDispatchService.sendSimpleMarkDownMsg(content);
        logger.info("📩 {} 已推送 {} 条日志，并更新 lastTs={},时间：{} 推送内容：{}",environmentName, hitLogs.size(), maxTs,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),content);
        return Mono.empty();
    }
}
