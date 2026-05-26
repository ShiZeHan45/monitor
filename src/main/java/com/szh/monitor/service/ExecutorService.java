package com.szh.monitor.service;

/**
 * 检测接口  可以实现基于SQL、HTTP等各种自定义检测，并推送消息
 */
public interface ExecutorService {
    void execute();
    void executeRetry();

    String getTitle();
}
