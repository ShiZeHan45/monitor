# Monitor 项目分析文档

## 项目概述

一个基于 Spring Boot 2.7.18 的**监控告警系统**，支持日志监听和 SQL 执行检查，通过企业微信机器人实时推送告警消息。

- **项目名**: `com.szh:monitor:1.0-SNAPSHOT`
- **Java 版本**: 1.8
- **运行端口**: 4000
- **构建产物**: `actuator.jar`

## 技术栈

| 技术 | 版本/说明 |
|------|-----------|
| Spring Boot | 2.7.18 |
| Spring WebFlux | 响应式 HTTP（调用 Grafana Loki） |
| MyBatis-Plus | 3.5.2 ORM |
| HikariCP | 多数据源连接池 |
| SQLite | 本地存储（执行记录、规则、消息队列） |
| PostgreSQL | 远程 SQL 检查目标库 |
| MySQL | 远程数据库驱动（备用） |
| Quartz | 调度器 |
| Micrometer + Prometheus | 指标采集 |
| Lombok | 代码生成 |

## 项目结构

```
src/main/java/com/szh/monitor/
├── ProxyApplication.java          # 启动入口，实现 CommandLineRunner
├── config/                         # 配置层
│   ├── BaseConfig.java            # 企业微信 Webhook 配置
│   ├── GrafanaConfig.java         # Grafana Loki 多环境配置
│   ├── LocalLogConfig.java        # 本地日志监控配置
│   ├── MonitorRules.java          # 监控规则定义
│   ├── SQLConfig.java             # SQL 检查配置
│   ├── ScheduleConfig.java        # 三个 TaskScheduler 线程池
│   ├── MultiDataSourceConfig.java # 多数据源动态注册
│   ├── SQLiteDataSourceConfig.java# SQLite 主库 + MyBatis 配置
│   └── TransactionManagerConfig.java
├── context/
│   ├── ExecuteJDBCContext.java    # SQL 执行状态管理（核心）
│   └── SpringContextUtil.java     # Spring 容器工具
├── entity/                         # MyBatis-Plus 实体
│   ├── LogCollectTimeInfo.java    # 日志采集时间戳
│   ├── MsgSendLog.java            # 消息发送记录
│   ├── SqlExecuteLog.java         # SQL 执行记录
│   └── SqlExecuteRule.java        # SQL 执行规则
├── enums/MsgType.java             # 消息类型（正常/异常）
├── exception/SQLExecutorFailException.java
├── form/
│   ├── MsgForm.java               # 消息表单
│   └── WechatMessage.java         # 企业微信消息体
├── mapper/                         # MyBatis-Plus Mapper
│   ├── LogCollectTimeInfoMapper.java
│   ├── MsgSendLogMapper.java
│   ├── SqlExecuteLogMapper.java
│   └── SqlExecuteRuleMapper.java
├── scheduled/                      # 定时任务
│   ├── ExecutorScheduler.java     # 每 4 分钟执行 SQL 检查
│   ├── ExecuteFailedRetry.java    # 每 5 分钟重试失败 SQL
│   └── ExecutorLogClear.java      # 每天清理过期日志
├── service/                        # 服务接口
│   ├── ExecutorService.java
│   ├── WatchService.java
│   ├── SendService.java
│   ├── LogCollectTimeInfoService.java
│   ├── MsgSendLogService.java
│   ├── SqlExecuteLogService.java
│   └── SqlExecuteRuleService.java
└── service/impl/                   # 服务实现
    ├── DispatchLogService.java    # 日志监听调度
    ├── GrafanaLogServiceImp.java  # Grafana Loki 日志监控（核心）
    ├── LocalLogFileServiceImp.java# 本地日志文件监控
    ├── SqlExecutorService.java    # SQL 执行引擎
    ├── SendDispatchService.java   # 消息路由
    ├── SendWechatService.java     # 企业微信推送（含免打扰）
    ├── LogCollectTimeInfoServiceImp.java
    ├── MsgSendLogServiceImp.java
    └── SqlExecuteLogServiceImp.java

src/main/resources/db/schema.sql    # SQLite 表结构
```

## 三大子系统

### 1. Grafana Loki 日志监控

```
GrafanaConfig → GrafanaLogServiceImp（每 30 秒轮询）
  → WebClient HTTP 请求 Loki API
  → 关键词匹配 + 排除过滤 + 上下文捕获
  → SendWechatService → 企业微信 Webhook
  → 持久化 lastTs 到 SQLite
```

- 支持多环境、多规则、多 Webhook
- 时间分片分页（30 分钟窗口），防重复处理
- 可配置工作日和工作时间段
- 关键词 + 排除关键词双重过滤

### 2. 本地日志文件监控

```
LocalLogFileServiceImp（守护线程）
  → Java WatchService 监听文件变更
  → RandomAccessFile 增量读取
  → 正则匹配 ERROR/Exception 等关键词
  → SHA-1 去重 → 企业微信推送
```

- 支持 UTF-8/ISO-8859-1 自动编码切换
- 可配置去重时间窗口
- 可配置上下文行数

### 3. SQL 执行检查

```
ExecutorScheduler（每 4 分钟）
  → 加载 sql_execute_rule 规则
  → 遍历多环境数据源
  → 执行 .sql 文件
  → 非空结果 = 告警 → 企业微信推送
  → 记录到 sql_execute_log

ExecuteFailedRetry（每 5 分钟）
  → 重试失败文件，成功自动恢复
```

- 每个 SQL 文件可独立配置：执行频率、时间窗口、每日上限
- 支持无限执行白名单
- 多数据源 HikariCP 连接池隔离

## 消息推送策略

- **正常时段**（08:00-20:00）：实时推送
- **免打扰时段**（20:00-08:00）：消息暂存 SQLite，次日 9:30 补推
- 支持的推送格式：Markdown、Text
- 每条消息间隔 5 秒，避免触发频率限制

## 数据库设计

**SQLite 本地库**（`/soft/sqlite/monitor.db`）：

| 表名 | 用途 |
|------|------|
| `sql_execute_log` | SQL 文件执行记录（次数、失败数） |
| `sql_execute_rule` | 每个文件/环境的执行规则 |
| `msg_send_log` | 消息推送记录（含待推送队列） |
| `log_collect_time_info` | Grafana 日志采集时间戳 |

**远程数据库**：通过配置动态注册 PostgreSQL/MySQL 数据源，仅用于执行检查 SQL。

## 线程模型

| 线程池 | 前缀 | 用途 |
|--------|------|------|
| `executorSQL` | `executor-` | 每 4 分钟执行 SQL 检查 |
| `retrySQL` | `retry-` | 每 5 分钟重试失败任务 |
| `grafanaLog` | `log-` | Grafana 日志轮询 |

## 启动流程

1. Spring Boot 初始化，MyBatis-Plus 建表（`schema.sql`）
2. `MultiDataSourceConfig` 动态注册所有远程数据源
3. `CommandLineRunner.run()`:
   - `DispatchLogService.startWatching()` → 启动日志监控守护线程
   - `LogCollectTimeInfoService.initLastTSMAP()` → 恢复 Grafana 时间戳

## 关键设计决策

- **WebFlux 而非 Spring MVC**：Grafana Loki HTTP 调用使用非阻塞 I/O
- **SQLite 而非内存**：执行状态、消息队列需要持久化，重启不丢失
- **策略模式**：`List<WatchService>`、`List<SendService>`、`List<ExecutorService>` 自动注入，方便扩展
- **配置文件排除版本控制**：`.gitignore` 排除 `*.yml`，敏感信息不提交
