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

import java.text.MessageFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrafanaLogServiceImp {
    Logger logger = LoggerFactory.getLogger(GrafanaLogServiceImp.class);
    private final TreeMap<String, WebClient> webClientMap = new TreeMap<>();
    private final Map<String, GrafanaConfig.GrafanaInfo> grafanaInfoMap = new HashMap<>();
    private final SendDispatchService sendDispatchService;
    private final LogCollectTimeInfoService logCollectTimeInfoService;
    private final Integer TIME = 30;
    private final Integer DEFAULT_LIMIT = 200;
    // 每个监控项独立记住上次处理的时间戳
    private final Map<String, Long> lastTsMap = new HashMap<>();

    public Map<String, GrafanaConfig.GrafanaInfo> getGrafanaInfoMap() {
        return grafanaInfoMap;
    }

    public void initLastTsMap(String key, Long lastTs) {
        lastTsMap.put(key, lastTs);
    }

    public GrafanaLogServiceImp(GrafanaConfig grafanaConfig, SendDispatchService sendDispatchService, LogCollectTimeInfoService logCollectTimeInfoService) {
        this.sendDispatchService = sendDispatchService;
        this.logCollectTimeInfoService = logCollectTimeInfoService;
        for (GrafanaConfig.GrafanaInfo grafanaInfo : grafanaConfig.getList()) {
            String basicAuth = Base64Utils.encodeToString(
                    (grafanaInfo.getUsername() + ":" + grafanaInfo.getPassword()).getBytes()
            );
            webClientMap.put(grafanaInfo.getEnvironmentName(), WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .codecs(config -> config.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                    .build());
            grafanaInfoMap.put(grafanaInfo.getEnvironmentName(), grafanaInfo);
        }
        logger.info("webClient初始化完成 {}", webClientMap.keySet());

    }

    @Scheduled(initialDelay = 10_000, fixedRate = 30_000)
    public void supplement() {
        for (Map.Entry<String, WebClient> entry : webClientMap.descendingMap().entrySet()) {
            GrafanaConfig.GrafanaInfo grafanaInfo = grafanaInfoMap.get(entry.getKey());
            for (MonitorRules item : grafanaInfo.getMonitors()) {
                if (!item.isEnabled()) {
                    continue;
                }
                int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
                if (grafanaInfo.getWeek() != null && !grafanaInfo.getWeek().contains(dayOfWeek)) {
                    continue;
                }
                if (grafanaInfo.getStartTime() != null && (LocalTime.now().isBefore(grafanaInfo.getStartTime()) || LocalTime.now().isAfter(grafanaInfo.getEndTime()))) {
                    continue;
                }
                try {
                    processMonitor(item, entry.getValue(), grafanaInfo);
                } catch (Exception e) {
                    logger.error("Monitor {} error", item.getName(), e);
                }
            }
        }
        logger.debug("---------------------------分隔符-------------------------------");

    }


    private void processMonitor(MonitorRules item, WebClient webClient, GrafanaConfig.GrafanaInfo grafanaInfo) {
        long now = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli();
        long globalStart  = lastTsMap.getOrDefault(grafanaInfo.getEnvironmentName() + "_" + item.getName(), LocalDateTime.now().minusMinutes(TIME)
                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli());

        LocalDateTime globalStartTime  = LocalDateTime.ofInstant(Instant.ofEpochMilli(globalStart), ZoneId.systemDefault());
        LocalDateTime globalEndTime  = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
        if (globalStartTime.plusMinutes(TIME).isBefore(globalEndTime)) {//这一步是为了补扫描 监控程序重启或者停止扫描期间产生的日志
            // 开始时间加TIME分钟如果大于结束时间  ，结束时间就用当前时间，反之 结束时间等于开始时间加TIME分钟
            globalEndTime = globalStartTime.plusMinutes(TIME);
        }
        logger.debug("环境：[{}]  微服务：[{}] 开始获取时间区间[{}~{}]内产生的日志进行分析", grafanaInfo.getEnvironmentName(), item.getName(),
                globalStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                globalEndTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

//        logger.debug("{}-{} 查询时间区间 {} ~ {} 产生的日志 ",grafanaInfo.getEnvironmentName(),item.getName(),startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        long globalEnd = globalEndTime.atZone(ZoneId.systemDefault())  // 使用系统默认时区
                .toInstant()
                .toEpochMilli();
        String baseUrl = grafanaInfo.getUrl();
        String dsId = grafanaInfo.getDatasourceId();


        String url = baseUrl + "/api/datasources/proxy/" + dsId + "/loki/api/v1/query_range";


//        webClient.get()
//                .uri(url + "?query={query}&start={start}&end={end}&limit={limit}",
//                        item.getQueryExpr(), start * 1_000_000, finalEndTime
//                                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
//                                .toInstant()
//                                .toEpochMilli() * 1_000_000, DEFAULT_LIMIT)
//                .retrieve()
//                .bodyToMono(Map.class)
//                .flatMap(body -> handleResult(grafanaInfo.getEnvironmentName(), item, body, finalEndTime, startTime))
//                .onErrorResume(e -> {
//                    logger.error("{}-{} ❌ WebClient 调用 Loki 失败", grafanaInfo.getEnvironmentName(), item.getName(), e);
//                    return Mono.empty();
//                })
//                .subscribe();
        // 🔥 核心：按TIME分钟分片 + 正向增量分页
        long currentStart = globalStart;
        while (currentStart < globalEnd) {
            LocalDateTime currentStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentStart), ZoneId.systemDefault());
            LocalDateTime currentEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(globalEnd), ZoneId.systemDefault());

            long sliceStart = currentStart;
            int sliceTotal = 0;
            while (true) {

                //时间分片读取 TIME分钟
                Map body =  webClient.get()
                        .uri(url + "?direction=forward&query={query}&start={start}&end={end}&limit={limit}",
                                item.getQueryExpr(), sliceStart  * 1_000_000, globalEnd* 1_000_000, DEFAULT_LIMIT)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .onErrorResume(e -> {
                            logger.error("{}-{} ❌ WebClient 调用 Loki 失败", grafanaInfo.getEnvironmentName(), item.getName(), e);
                            return Mono.empty();
                        })
                        .block();

                int batchCount = 0;
                long batchMaxTs = sliceStart;
                List<String> hitLogs = new ArrayList<>();

                if (body != null && !body.isEmpty()) {
                    Map data = (Map) body.get("data");
                    if (data != null) {
                        List<Map<String, Object>> streams = (List<Map<String, Object>>) data.get("result");
                        if (streams != null) {
                            for (Map<String, Object> stream : streams) {
                                List<List<Object>> values = (List<List<Object>>) stream.get("values");
                                if (values == null || values.isEmpty()) continue;

                                // direction=forward时，日志已经是按时间升序排列，无需再排序
                                batchCount += values.size();

                                for (List<Object> entry : values) {
                                    long ts = Long.parseLong((String) entry.get(0)) / 1_000_000;
                                    String log = (String) entry.get(1);
                                    if (ts > batchMaxTs) batchMaxTs = ts;

                                    if (ts <= lastTsMap.getOrDefault(grafanaInfo.getEnvironmentName() + "_" + item.getName(), globalStart)) {
                                        continue;
                                    }

                                    if (item.getKeywords().stream().anyMatch(log::contains)) {
                                        int endIdx = Math.min(values.indexOf(entry) + item.getContextLines(), values.size());
                                        List<String> context = values.subList(values.indexOf(entry), endIdx).stream()
                                                .map(v -> (String) v.get(1))
                                                .collect(Collectors.toList());
                                        String waitPushContext = String.join("\n", context);

                                        if (item.getExclusionKeywords().stream().noneMatch(waitPushContext::contains)) {
                                            hitLogs.add(waitPushContext);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                sliceTotal += batchCount;

                // 更新全局lastTs
                if (batchMaxTs > lastTsMap.getOrDefault(grafanaInfo.getEnvironmentName() + "_" + item.getName(), globalStart)) {
                    lastTsMap.put(grafanaInfo.getEnvironmentName() + "_" + item.getName(), batchMaxTs);
                    logCollectTimeInfoService.updateOrSave(grafanaInfo.getEnvironmentName(), item.getName(), batchMaxTs);
                }

                // 推送异常日志
                if (!hitLogs.isEmpty()) {
                    String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```",
                            grafanaInfo.getEnvironmentName(),
                            hitLogs.stream().collect(Collectors.joining("\n---\n")));
                    if (content.length() > 1500) {
                        content = content.substring(0, 1500) + "\n...（内容过长已截断）";
                    }
                    sendDispatchService.sendSimpleMarkDownMsg(content);
                    logger.info("📩 {} 已推送 {} 条异常日志", grafanaInfo.getEnvironmentName(), hitLogs.size());
                }

                // 🔥 分页终止条件：本次返回数量 < limit，说明该分片已无更多日志
                if (batchCount < DEFAULT_LIMIT) {
                    logger.debug("环境：[{}]  微服务：[{}]  分片[{}~{}]处理完成，共获取 {} 条日志",grafanaInfo.getEnvironmentName(),item.getName(),
                            currentStartLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            currentEndLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            sliceTotal);
                    break;
                } else {
                    logger.debug("环境：[{}]  微服务：[{}]  从[{}~{}]拉取数据，共{}条",grafanaInfo.getEnvironmentName(),item.getName(),LocalDateTime.ofInstant(Instant.ofEpochMilli(sliceStart), ZoneId.systemDefault()),
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(globalEnd), ZoneId.systemDefault()),batchCount);
//                    // 🔥 下一次从最后一条日志的时间戳+1毫秒开始拉取
//                    logger.debug("分片[{}~{}]本次拉取满{}条，继续从 {} 拉取",
//                            currentStartLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                            currentEndLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                            DEFAULT_LIMIT,
//                            LocalDateTime.ofInstant(Instant.ofEpochMilli(batchMaxTs), ZoneId.systemDefault())
//                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    sliceStart = batchMaxTs + 1;
                }

            }

            currentStart = globalEnd;

        }
        logger.info("环境：[{}]  微服务：[{}] 时间区间[{}~{}]处理完成",
                grafanaInfo.getEnvironmentName(), item.getName(),
                globalStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                globalEndTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

    }

//    private Mono<Void> handleResult(String environmentName, MonitorRules item, Map body, LocalDateTime endTime, LocalDateTime startTime) {
//        if (body == null) {
//            logger.debug("{}该时间区间无日志记录", environmentName);
//            return Mono.empty();
//        }
//
//
//        Map data = (Map) body.get("data");
//        if (data == null) {
//            logger.debug("{}该时间区间无日志记录", environmentName);
//            return Mono.empty();
//        }
//
//
//        List result = (List) data.get("result");
//        if (result == null) {
//            logger.debug("{}该时间区间无日志记录", environmentName);
//            return Mono.empty();
//        }
//        boolean flag = startTime.plusMinutes(TIME).isBefore(endTime);
//        int count = 0;
//        for (Object obj : result) {
//            Map stream = (Map) obj;
//            List<List> values = (List<List>) stream.get("values");
//            if (values == null) continue;
//            count = count + values.size();
//        }
//
//
//        long lastTs = lastTsMap.getOrDefault(environmentName + "_" + item.getName(), startTime
//                .atZone(ZoneId.systemDefault())  // 使用系统默认时区
//                .toInstant()
//                .toEpochMilli());
//
//        List<String> hitLogs = new ArrayList<>();
//
//// 更新 lastTs
//        long maxTs = lastTs;
//        for (Object obj : result) {
//            Map stream = (Map) obj;
//            List<List> values = (List<List>) stream.get("values");
//            if (values == null) continue;
//            if (values != null && !values.isEmpty()) {
//                values.sort(Comparator.comparing(v -> Long.parseLong((String) v.get(0))));
//            }
//
//// values: [ [timestamp, log], ... ]
//            for (int i = 0; i < values.size(); i++) {
//                List entry = values.get(i);
//                long ts = Long.parseLong((String) entry.get(0)) / 1_000_000;
//                String log = (String) entry.get(1);
//                if (ts > maxTs && !flag) maxTs = ts;
//                if (ts <= lastTs) {
//                    continue;
//                }
//                //匹配上关键词 同时匹配不上移除关键词
//                if (item.getKeywords().stream().anyMatch(log::contains) /*&& item.getExclusionKeywords().stream().noneMatch(log::contains)*/) {
//                    int end = Math.min(i + item.getContextLines(), values.size());
//                    List<String> context = values.subList(i, end).stream()
//                            .map(v -> (String) v.get(1))
//                            .collect(Collectors.toList());
//                    String waitPushContext = String.join("\n", context);
//
//                    // 再匹配一下 因为需要对总输出的结果进行关键词匹配忽略
//                    if (item.getExclusionKeywords().stream().anyMatch(waitPushContext::contains)) {
//                        logger.debug("捕获到忽略推送日志， 忽略推送词汇字典： {} 待推送文本： {}", item.getExclusionKeywords(), waitPushContext);
//                    } else {//没有在忽略推送字典里的日志就进入推送集合中
//                        hitLogs.add(waitPushContext);
//                        break;
//                    }
//
//                }
//            }
//        }
//        lastTsMap.put(environmentName + "_" + item.getName(), maxTs);
//        logCollectTimeInfoService.updateOrSave(environmentName, item.getName(), maxTs);
//
//        logger.debug("环境：[{}]  微服务：[{}] 获取到[{}]条日志,\n时间范围[{}~{}],此范围内实际最新的一笔日志时间为：{} ，\n匹配关键词为：{}，匹配到{}条",
//                environmentName, item.getName(), count,
//                startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs),
//                        ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                item.getKeywords(), hitLogs.size());
//
//// 无命中
//        if (hitLogs.isEmpty()) return Mono.empty();
//
//// 聚合推送
//        String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```", environmentName, hitLogs.stream().collect(Collectors.joining("")));
//        if (content.length() > 1500) {
//            logger.info("推送内容超长，截取1500字符");
//            content = content.substring(0, 1500);
//        }
//        sendDispatchService.sendSimpleMarkDownMsg(content);
//        logger.info("📩 {} 已推送 {} 条日志，并更新 lastTs={},时间：{} 推送内容：{}", environmentName, hitLogs.size(), maxTs,
//                LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), content);
//        return Mono.empty();
//    }

}
