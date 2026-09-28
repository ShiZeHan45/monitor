package com.szh.monitor.form;

/**
 * 日志下载请求参数（下载与预览共用）。
 */
public class LogDownloadRequest {

    /** 环境名（对应 grafana_data_source.environment_name） */
    private String env;

    /** 服务名（对应 grafana_monitor_rule.name） */
    private String service;

    /** 开始时间 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 结束时间 yyyy-MM-dd HH:mm:ss */
    private String endTime;

    /** 包含关键词，逗号分隔，任一命中保留该行 */
    private String includeKeywords;

    /** 排除关键词，逗号分隔，任一命中跳过该行 */
    private String excludeKeywords;

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getIncludeKeywords() {
        return includeKeywords;
    }

    public void setIncludeKeywords(String includeKeywords) {
        this.includeKeywords = includeKeywords;
    }

    public String getExcludeKeywords() {
        return excludeKeywords;
    }

    public void setExcludeKeywords(String excludeKeywords) {
        this.excludeKeywords = excludeKeywords;
    }
}
