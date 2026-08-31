# 监控系统项目分析文档

> **文档目的**: 帮助 AI 模型或其他开发人员快速理解项目架构、代码逻辑和开发流程  
> **项目类型**: Spring Boot 后端监控系统  
> **最后更新**: 2026-08-31

---

## 📋 目录

1. [项目概述](#1-项目概述)
2. [技术栈详解](#2-技术栈详解)
3. [项目结构](#3-项目结构)
4. [核心功能模块](#4-核心功能模块)
5. [架构设计说明](#5-架构设计说明)
6. [核心类详解](#6-核心类详解)
7. [API 接口文档](#7-api-接口文档)
8. [数据库设计](#8-数据库设计)
9. [配置文件详解](#9-配置文件详解)
10. [定时任务系统](#10-定时任务系统)
11. [开发历史（Git Log）](#11-开发历史git-log)
12. [部署流程](#12-部署流程)
13. [开发注意事项](#13-开发注意事项)
14. [常见问题与解决](#14-常见问题与解决)
15. [项目规范](#15-项目规范)

---

## 1. 项目概述

### 1.1 基本信息

| 属性 | 值 |
|------|-----|
| 项目名称 | 监控系统 (Monitor) |
| 项目类型 | Spring Boot 2.7.18 单模块应用 |
| 打包方式 | Maven (JAR 可执行包) |
| 产物名 | `target/actuator.jar` |
| 服务端口 | 4000 |
| 监控端口 | 18081 |
| 数据库 | SQLite (本地) + PostgreSQL/MySQL (远程) |

### 1.2 核心功能

```
┌─────────────────────────────────────────────────────────┐
│                      监控系统架构                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────┐ │
│  │ Grafana Loki │ →  │   日志监听   │ →  │           │ │
│  │ 远程日志     │    │  关键词匹配   │    │ 企业微信   │ │
│  └──────────────┘    └──────────────┘    │  推送     │ │
│  ┌──────────────┐    ┌──────────────┐    │(支持数据源 │ │
│  │ 本地日志文件  │ →  │  上下文截取   │ →  │ 级webhook)│ │
│  └──────────────┘    └──────────────┘    └───────────┘ │
│  ┌──────────────┐    ┌──────────────┐                  │
│  │ SQL 执行器   │ →  │  多数据源     │ →  ┌───────────┐ │
│  │ (定时/手动)  │    │  PostgreSQL   │ →  │ 企业微信   │ │
│  │ 失败重试     │    │  MySQL       │    │ 推送       │ │
│  └──────────────┘    └──────────────┘    │(数据源级   │ │
│  ┌──────────────┐    ┌──────────────┐    │ webhook)  │ │
│  │ 健康检查     │ →  │ 自动标记     │ →  └───────────┘ │
│  │ (5分钟)      │    │ 在线/离线    │                  │
│  └──────────────┘    └──────────────┘                  │
│  ┌──────────────┐                                      │
│  │ 操作日志     │ → AOP 切面记录所有增删改查操作         │
│  └──────────────┘                                      │
│  ┌──────────────┐                                      │
│  │ 仪表盘首页   │ → 卡片式展示关键指标 + 推送分类统计     │
│  └──────────────┘                                      │
└─────────────────────────────────────────────────────────┘
```

### 1.3 支持的监控环境

- **Grafana 环境**: 郑州生产、东莞生产、南昌生产、测试环境、开发环境
- **SQL 数据源**: 郑州生产、南昌生产、莲上-南澳自来水生产

### 1.4 配置管理演进

| 阶段 | 描述 |
|------|------|
| 初期 | 数据源配置在 `application.yml` 硬编码 |
| 当前 | 首次启动从 YML 导入 SQLite 数据库，后续通过 Web 页面动态管理；`SqlConfigService.refreshConfig()` 重建连接池和 JdbcTemplate Bean |
| 最新 | 全局 webhook、免打扰时段、补推时间也已迁移到 `system_config` 表，通过 `system-config.html` 页面配置 |

---

## 2. 技术栈详解

### 2.1 核心技术

```xml
<!-- Spring Boot 2.7.18 - 基础框架 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
</parent>

<!-- 关键依赖 -->
spring-boot-starter          # 核心启动
spring-boot-starter-web      # Web MVC
spring-boot-starter-webflux  # 响应式 Web (Grafana Loki 调用)
spring-boot-starter-jdbc     # JDBC 支持
spring-boot-starter-actuator # 应用监控 / Prometheus
spring-boot-starter-quartz   # 定时任务
mybatis-plus-boot-starter    # ORM 框架 (含分页插件)
micrometer-registry-prometheus # Prometheus 指标
spring-boot-starter-aop      # 操作日志 AOP
```

### 2.2 数据存储

| 数据库 | 驱动版本 | 用途 |
|--------|----------|------|
| SQLite | 3.45.1.0 | 本地持久化：数据源配置、规则、推送日志、操作日志、采集统计、系统配置 |
| PostgreSQL | 42.7.7 | 远程 SQL 检测（郑州生产、南昌生产） |
| MySQL | 8.0.30 | 远程 SQL 检测（莲上-南澳自来水生产） |

### 2.3 响应式编程

```xml
<!-- Reactor 版本统一管理，避免冲突 -->
reactor-core: 3.4.34
reactor-netty-core: 1.0.39
reactor-netty-http: 1.0.39
```
> Grafana Loki 日志采集和健康检查使用 WebClient（Reactor Netty），需统一版本避免冲突。

---

## 3. 项目结构

### 3.1 目录树

```
z:\monitor/
│
├── pom.xml                           # Maven 配置
│                                     # - 打包名: actuator
│                                     # - Java: 1.8
│
├── DEVELOPMENT_FLOW.md               # 研发过程约定
├── PROJECT_ANALYSIS.md               # 本文件，项目分析文档
├── README.md, 迭代研发交付规范.md     # 项目说明 / 流程规范
├── deploy.exp                        # Expect 自动部署脚本
├── app.log                          # 应用运行时日志
│
└── src/
    ├── main/
    │   ├── java/com/szh/monitor/
    │   │   │
    │   │   ├── ProxyApplication.java     # ⭐ 启动类
    │   │   │                             # - @SpringBootApplication
    │   │   │                             # - @EnableScheduling, @EnableAsync
    │   │   │
    │   │   ├── annotation/               # 注解定义
    │   │   │   └── OperationLog.java     # 操作日志注解（@OperationLog）
    │   │   │
    │   │   ├── aspect/                   # AOP 切面
    │   │   │   ├── OperationLogAspect.java    # ⭐ 操作日志核心切面
    │   │   │   └── OperationLogServiceProxy.java # 异步日志代理
    │   │   │
    │   │   ├── config/                   # 配置类
    │   │   │   ├── BaseConfig.java       # 企业微信 Webhook 配置
    │   │   │   ├── GrafanaConfig.java    # Grafana Loki YML 映射
    │   │   │   ├── LocalLogConfig.java   # 本地日志配置
    │   │   │   ├── MonitorRules.java     # 监控规则配置映射
    │   │   │   ├── SQLConfig.java        # SQL 执行配置
    │   │   │   ├── MultiDataSourceConfig.java  # SQL 多数据源 YML 映射
    │   │   │   ├── SQLiteDataSourceConfig.java # SQLite 主数据源
    │   │   │   ├── MybatisPlusConfig.java      # MyBatis Plus 分页插件
    │   │   │   ├── ScheduleConfig.java         # 定时任务线程池配置（poolSize=1）
    │   │   │   ├── TransactionManagerConfig.java # 事务管理器
    │   │   │   ├── WebConfig.java       # CORS / 静态资源
    │   │   │   ├── MultipartConfig.java # 文件上传
    │   │   │   ├── GrafanaDataInitializer.java # 首次启动 Grafana 数据导入
    │   │   │   └── SqlDataInitializer.java     # 首次启动 SQL 数据导入
    │   │   │
    │   │   ├── context/                 # 上下文工具
    │   │   │   ├── ExecuteJDBCContext.java # ⭐ JDBC 执行上下文（规则+计数+失败管理）
    │   │   │   └── SpringContextUtil.java  # Spring Bean 动态获取
    │   │   │
    │   │   ├── controller/              # REST API 控制器
    │   │   │   ├── MonitorController.java    # ⭐ 核心 API：统计/规则/SQL文件/环境/调试
    │   │   │   ├── GrafanaController.java    # Grafana 数据源+规则 CRUD
    │   │   │   ├── SqlController.java        # SQL 数据源 CRUD
    │   │   │   ├── OperationLogController.java # 操作日志查询
    │   │   │   └── SystemConfigController.java # 系统配置（webhook/免打扰/补推时间）
    │   │   │
    │   │   ├── entity/                  # 数据库实体（8 个）
    │   │   │   ├── GrafanaDataSource.java    # Grafana 数据源
    │   │   │   ├── GrafanaMonitorRule.java   # Grafana 监控规则（含采集进度字段）
    │   │   │   ├── SqlDataSource.java        # SQL 数据源
    │   │   │   ├── SqlExecuteLog.java        # SQL 执行日志
    │   │   │   ├── SqlExecuteRule.java       # SQL 执行规则
    │   │   │   ├── MsgSendLog.java           # 消息推送日志
    │   │   │   ├── OperationLog.java         # 操作日志
    │   │   │   └── SystemConfig.java         # 系统配置（键值对）
    │   │   │
    │   │   ├── mapper/                  # MyBatis Mapper（8 个）
    │   │   │   ├── GrafanaDataSourceMapper.java
    │   │   │   ├── GrafanaMonitorRuleMapper.java
    │   │   │   ├── SqlDataSourceMapper.java
    │   │   │   ├── SqlExecuteLogMapper.java
    │   │   │   ├── SqlExecuteRuleMapper.java
    │   │   │   ├── MsgSendLogMapper.java
    │   │   │   ├── OperationLogMapper.java
    │   │   │   └── SystemConfigMapper.java
    │   │   │
    │   │   ├── service/                 # 服务接口
    │   │   │   ├── WatchService.java         # 日志监听
    │   │   │   ├── ExecutorService.java      # SQL 执行（含 executeSingle 默认方法）
    │   │   │   ├── SendService.java          # 消息推送
    │   │   │   ├── SqlDataSourceService.java
    │   │   │   ├── GrafanaDataSourceService.java
    │   │   │   ├── GrafanaMonitorRuleService.java
    │   │   │   ├── SqlExecuteLogService.java
    │   │   │   ├── SqlExecuteRuleService.java
    │   │   │   ├── MsgSendLogService.java
    │   │   │   ├── OperationLogService.java
    │   │   │   └── SystemConfigService.java
    │   │   │
    │   │   ├── service/impl/            # 服务实现（15 个）
    │   │   │   ├── DispatchLogService.java       # 日志监听分发
    │   │   │   ├── GrafanaLogServiceImp.java     # ⭐ Grafana Loki 日志采集
    │   │   │   ├── LocalLogFileServiceImp.java   # 本地日志监听
    │   │   │   ├── SqlExecutorService.java       # ⭐ SQL 执行
    │   │   │   ├── SendWechatService.java        # ⭐ 企业微信推送
    │   │   │   ├── SendDispatchService.java      # 消息分发
    │   │   │   ├── SqlConfigService.java         # SQL 动态配置（连接池管理）
    │   │   │   ├── SqlDataSourceServiceImp.java
    │   │   │   ├── GrafanaDataSourceServiceImp.java
    │   │   │   ├── GrafanaMonitorRuleServiceImp.java
    │   │   │   ├── SqlExecuteLogServiceImp.java
    │   │   │   ├── SqlExecuteRuleServiceImp.java
    │   │   │   ├── MsgSendLogServiceImp.java
    │   │   │   ├── OperationLogServiceImp.java
    │   │   │   └── SystemConfigServiceImp.java
    │   │   │
    │   │   ├── scheduled/               # 定时任务（5 个）
    │   │   │   ├── ExecutorScheduler.java         # SQL 执行调度（4分钟，按环境隔离）
    │   │   │   ├── ExecuteFailedRetry.java        # 失败重试（5分钟）
    │   │   │   ├── ExecutorLogClear.java          # 日志清理（每天凌晨）
    │   │   │   ├── GrafanaDataSourceHealthChecker.java # Grafana 健康检查（5分钟）
    │   │   │   └── SqlDataSourceHealthChecker.java    # SQL 健康检查（5分钟，直连）
    │   │   │
    │   │   ├── form/                    # 表单对象
    │   │   │   ├── MsgForm.java         # 消息表单（含 webhook 字段）
    │   │   │   └── WechatMessage.java   # 企业微信消息实体
    │   │   │
    │   │   ├── vo/                      # 视图对象
    │   │   │   └── MsgVO.java
    │   │   │
    │   │   ├── enums/
    │   │   │   └── MsgType.java         # 消息类型枚举
    │   │   │
    │   │   └── exception/
    │   │       └── SQLExecutorFailException.java # SQL 执行异常（携带失败文件名列表）
    │   │
    │   ├── resources/
    │   │   ├── application.yml         # ⭐ 主配置文件
    │   │   ├── db/schema.sql           # SQLite 表结构（含数据迁移SQL）
    │   │   ├── db/datainit.sql         # 初始数据
    │   │   └── static/                 # 前端静态页面（8 个）
    │   │       ├── index.html          # ⭐ 仪表盘首页
    │   │       ├── grafana-config.html # Grafana 数据源配置（含采集起始时间）
    │   │       ├── sql-config.html     # SQL 数据源配置
    │   │       ├── sql-rules.html      # SQL 执行规则管理
    │   │       ├── sql-upload.html     # SQL 文件上传/编辑
    │   │       ├── push-records.html   # 推送记录查询
    │   │       ├── operation-logs.html # 操作日志查询（flatpickr 日期范围）
    │   │       └── system-config.html  # 推送配置（webhook/免打扰/补推时间）
    │   │
    │   └── resources/mapper/           # MyBatis XML（部分用注解替代）
    │
    └── test/java/...                  # 测试代码
```

### 3.2 核心文件说明

| 文件路径 | 重要性 | 说明 |
|----------|--------|------|
| `ProxyApplication.java` | ⭐⭐⭐ | 启动类，启用 @Async/@Scheduling |
| `application.yml` | ⭐⭐⭐ | 配置文件：端口、数据源、监控规则、webhook 兜底 |
| `pom.xml` | ⭐⭐ | Maven 配置，含 Reactor 版本锁定 |
| `GrafanaLogServiceImp.java` | ⭐⭐⭐ | Grafana Loki 日志采集（30s 间隔，连接池+重试+分片） |
| `SqlExecutorService.java` | ⭐⭐⭐ | SQL 执行检测，支持数据源级 webhook、按环境隔离 |
| `SendWechatService.java` | ⭐⭐⭐ | 企业微信推送，免打扰+补推（DB 配置时间） |
| `SqlConfigService.java` | ⭐⭐⭐ | 动态连接池管理，Bean 注册 |
| `SystemConfigServiceImp.java` | ⭐⭐⭐ | 系统配置缓存（webhook/免打扰/补推时间） |
| `OperationLogAspect.java` | ⭐⭐ | 操作日志 AOP 切面，字段变更对比 |
| `ExecuteJDBCContext.java` | ⭐⭐ | SQL 执行规则校验 + 执行计数 + 失败管理 |
| `GrafanaDataSourceHealthChecker.java` | ⭐⭐ | Grafana 健康检查（WebClient + 连接池 + 重试） |
| `SqlDataSourceHealthChecker.java` | ⭐⭐ | SQL 健康检查（DriverManager 直连） |
| `MonitorController.java` | ⭐⭐ | 核心 API：统计、规则、文件、环境、调试 |

---

## 4. 核心功能模块

### 4.1 日志监听模块

**职责**: 监听 Grafana Loki 和本地日志文件，检测错误并推送告警

**核心类**: 
- `GrafanaLogServiceImp.java` - Grafana Loki 远程日志（主力）
- `LocalLogFileServiceImp.java` - 本地日志文件

**Grafana 日志采集流程**:
```
1. @Scheduled(fixedRate=30s) 定时触发 supplement()（仅 @Scheduled，无 @Async）
2. 锁内快照 entry 列表（毫秒级），网络调用放到锁外执行（避免阻塞 refreshConfig）
3. 遍历启用且在线的数据源 → 遍历各监控规则
4. 检查星期/时间段约束
5. 从 lastTsMap 获取最后采集时间戳（取不到时直接查 DB 兜底）
6. 调用 Loki API（/api/datasources/proxy/{dsId}/loki/api/v1/query_range）
7. WebClient 连接池（maxConnections=10, timeout=120s）
8. 失败自动重试（最多 2 次，间隔 1s）
9. 分片拉取：每次最多 500 条，超过则继续拉取下个窗口（无 30 分钟范围截断）
10. 解析返回的 streams → values
11. 关键词匹配（需包含关键字）→ 排除关键词过滤
12. 截取上下文（contextLines 行）
13. 去重：按 lastTsMap 跳过已处理的时间戳
14. 推送：优先使用规则级 webhook → 数据源级 webhook
15. 更新 lastTsMap 和 grafana_monitor_rule（持久化 last_ts/last_time/采集计数）
```

**采集时间配置**: 通过 `grafana-config.html` 规则编辑弹窗修改，调 `PUT /api/grafana/rules/{id}/last-ts` 同步更新 DB 和内存 `lastTsMap`。修改规则（如排除关键词）后 `updateRule()` 会调 `refreshConfig()` 刷新内存缓存。

### 4.2 SQL 检测模块

**职责**: 定时执行 SQL 脚本，检测业务异常并推送告警

**核心类**:
- `SqlExecutorService.java` - SQL 执行逻辑（`executeSingle()` 按环境隔离）
- `ExecutorScheduler.java` - 定时调度（每 4 分钟）
- `ExecuteFailedRetry.java` - 失败重试（每 5 分钟）
- `ExecuteJDBCContext.java` - 执行规则校验 + 计数 + 失败管理

**功能流程**:
```
1. ExecutorScheduler 加载各环境的执行规则到 ExecuteJDBCContext
2. 遍历启用在线的数据源（executeSingle 单环境执行，异常隔离）
3. 检查数据源星期/时间段配置（week/startTime/endTime），非执行时段跳过
4. 扫描 SQL 目录（/soft/monitor）获取 .sql 文件列表
5. 过滤：只执行有规则的 SQL 文件
6. 对每个文件：
   a. executeAble() 检查 — 执行窗口/次数限制/频率
   b. 执行 SQL 查询
   c. 查询结果有数据 = 异常 → 推送告警
   d. 记录执行日志
7. 执行失败的文件：
   - 非"无上限次数"文件 → 记录失败计数
   - SQLExecutorFailException 抛出 → 失败计数递增
   - 每 8 次失败推送一次告警
8. 全部成功 → 清空失败计数
```

### 4.3 企业微信推送模块

**职责**: 统一消息推送，支持免打扰时段和数据源级 webhook

**核心类**:
- `SendWechatService.java` - 微信 API 调用
- `SendDispatchService.java` - 消息分发（遍历所有 SendService）

**推送逻辑**:
```java
sendMsg(MsgForm):
  // 判断消息来源
  // SQL 场景 → 优先使用 MsgForm.webhook（来自数据源配置）
  //          → 无则使用 systemConfigService.getWechatWebhook()（DB 配置）
  //          → 再无则使用 baseConfig.wechatWebhook（YML 兜底）
  // Grafana 场景 → 优先使用规则 webhook
  //              → 无则使用数据源 webhook
  //              → 再无则使用 systemConfigService.getLogWechatWebhook()
  //              → 再无则使用 baseConfig.logWechatWebhook

sendMsgAndStore(msg, msgType, webhook, log):
  // 免打扰：quiet_start(默认20) ~ quiet_end(默认8) → sendStatus=false，补推
  // 非免打扰：POST 企业微信 → sendStatus=true
  // msgType: SQL 用 "text"，日志用 "markdown"（首页统计按此区分）
  // 持久化到 msg_send_log 表
```

**补推流程**:
```java
@Scheduled(fixedRate = 60_000)  // 每分钟轮询，时间由 DB 配置
pushMsg():
  // 从 DB 读取 push_time（默认 09:30），仅在目标小时+分钟内执行一次
  // 查询所有 sendStatus=false 的记录
  // 每条间隔 5 秒发送
  // 每条独立保存，失败只跳过单条（无 @Transactional）
```

### 4.4 Web 管理界面

**前端技术**: 原生 HTML + Tailwind CSS + Font Awesome

**页面列表**:

| 页面 | 功能 | 新增特性 |
|------|------|----------|
| `index.html` | 仪表盘首页 | 关键指标卡片、推送分类统计（按 msgType）、数据源在线状态、日志采集统计 |
| `grafana-config.html` | Grafana 数据源/规则 CRUD | 规则编辑含采集起始时间 flatpickr、规则级 webhook、数据源ID自动获取（填URL/用户名/密码后自动查Loki id）、星期圆形多选、开始/结束时间用 time 选择器 |
| `sql-config.html` | SQL 数据源 CRUD | 动态管理，支持 webhook/星期多选/时段配置、开始/结束时间用 time 选择器 |
| `sql-rules.html` | SQL 执行规则管理 | 环境下拉只加载 SQL 数据源环境、批量复制规则到新环境（防重） |
| `sql-upload.html` | SQL 文件上传/在线编辑 | 内容编辑、文件删除 |
| `push-records.html` | 推送记录查询 | 分页查询，环境下拉含全部环境 |
| `operation-logs.html` | 操作日志 | flatpickr 日期范围组件、快捷选项、类型/模块筛选 |
| `system-config.html` | 推送配置 | webhook/免打扰时段/补推时间配置 |

### 4.5 操作日志系统

**职责**: 记录用户所有操作行为，支持变更前后对比

**核心类**:
- `@OperationLog` 注解 — 标注在 Controller 方法上
- `OperationLogAspect` — AOP 切面，环绕增强
- `OperationLogServiceProxy` — 异步持久化代理
- `OperationLogController` — 查询 API

**记录信息**: IP 地址、User-Agent、操作类型（VISIT/CREATE/EDIT/DELETE）、模块、详情（含字段变更对比）、耗时

**字段变更对比逻辑**:
```
1. 通过实体类名匹配字段映射表（SqlExecuteRule/SqlDataSource/GrafanaDataSource/GrafanaMonitorRule）
2. 遍历映射字段，从新旧实体反射取值
3. 新值为 null → 跳过（该字段未被本次操作涉及）
4. 新旧不同 → 记录 "【字段名】修改前 旧值 修改后 新值"
5. 格式化：Boolean/1/0 → 是/否，长文本截断 50 字
```

### 4.6 健康检查模块

**职责**: 定时检查数据源在线状态（7×24 小时运行，不受执行时段限制）

**Grafana 健康检查** (`GrafanaDataSourceHealthChecker`):
- 每 5 分钟检查一次
- 使用 WebClient 调用 `/api/org` API
- 独立连接池（health-check-pool）
- 最多重试 2 次
- **在线判断只看是否收到 HTTP 200 响应**（`response != null`），不依赖响应体非空——部分 Grafana（如郑州）的 `/api/org` 返回空 body，若用 `isEmpty()` 判断会误判离线

**SQL 健康检查** (`SqlDataSourceHealthChecker`):
- 每 5 分钟检查一次
- 使用 `DriverManager.getConnection()` **直连**（不通过 JdbcTemplate/HikariCP 池）
- 直连避免与 SQL 执行器争用连接池导致 `SELECT 1` 超时误判离线
- 失败时打印错误日志并标记离线

### 4.7 系统配置模块（system_config）

**职责**: 将原本写死在 YML 的全局配置迁移到数据库，支持页面动态修改

**配置项**:
| 键 | 默认值 | 说明 |
|----|--------|------|
| `wechat_webhook` | YML 值 | SQL 推送全局 webhook |
| `log_wechat_webhook` | YML 值 | 日志推送全局 webhook |
| `quiet_start` | 20 | 免打扰开始小时 |
| `quiet_end` | 8 | 免打扰结束小时 |
| `push_time` | 09:30 | 每日补推时间 |

---

## 5. 架构设计说明

### 5.1 分层架构

```
┌──────────────────────────────────────────────┐
│         Web 层 (Controllers)                  │
│  MonitorController, GrafanaController,       │
│  SqlController, OperationLogController,      │
│  SystemConfigController                      │
├──────────────────┬───────────────────────────┤
│                  │ @OperationLog AOP 切面     │
├──────────────────┴───────────────────────────┤
│           Service 层 (Services)               │
│  GrafanaLogService, SqlExecutorService,       │
│  SendWechatService, SqlConfigService,         │
│  SystemConfigServiceImp                       │
├──────────────────┬───────────────────────────┤
│                  │ 策略模式分发               │
│                  │ WatchService → 日志监听    │
│                  │ ExecutorService → SQL检测  │
│                  │ SendService → 消息推送     │
├──────────────────┴───────────────────────────┤
│           Mapper 层 (MyBatis-Plus)            │
│  MyBatisPlusInterceptor (分页插件 DbType.SQLITE)│
├──────────────────┬───────────────────────────┤
│                  │ 动态数据源路由              │
├──────────────────┴───────────────────────────┤
│           数据层 (Data Sources)               │
│  SQLite (本地) + PostgreSQL/MySQL (远程动态)  │
│  HikariCP 连接池（动态创建/销毁）              │
└──────────────────────────────────────────────┘
```

### 5.2 启动初始化流程

```
Spring Boot 启动
  │
  ├─→ DataSourceInitializer (SQLite 建表)
  │
  ├─→ SqlConfigService.@PostConstruct          │
  │     └─ refreshConfig() → 从 DB 读启用数据源  │
  │           ├─ 创建 HikariCP 连接池            │
  │           ├─ 创建 JdbcTemplate Bean          │
  │           └─ 注册到 ExecuteJDBCContext      │
  │
  ├─→ GrafanaLogServiceImp.@PostConstruct
  │     └─ refreshConfig() → 从 DB 读 Grafana 配置
  │           ├─ 创建 WebClient（带连接池+Basic Auth）
  │           ├─ 构建监控规则内存缓存
  │           └─ loadLastTsFromRules() ← 从 grafana_monitor_rule 恢复 lastTsMap
  │
  ├─→ SystemConfigServiceImp.@PostConstruct
  │     └─ refreshCache() → 加载 system_config 到内存缓存
  │
  ├─→ SqlDataInitializer (首次启动时)
  │     └─ 从 YML 导入数据到 DB
  │         └─ sqlConfigService.refreshConfig() （导入后刷新）
  │
  └─→ GrafanaDataInitializer (首次启动时)
        └─ 从 YML 导入数据到 DB
            └─ grafanaLogService.refreshConfig() （导入后刷新）
```

### 5.3 定时任务调度

```
┌──────────────────────────────────────────────┐
│       @EnableScheduling + 各线程池             │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │ GrafanaLogServiceImp.supplement()      │  │
│  │ 触发: @Scheduled(fixedRate=30s)        │  │
│  │ 线程池: grafanaLog (poolSize=1)        │  │
│  │ 任务: 遍历所有 Grafana 数据源/规则采集日志 │  │
│  │ 注意: 无 @Async；锁内只做快照，网络调用放锁外 │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ ExecutorScheduler.executor()           │  │
│  │ 触发: @Async + @Scheduled(fixedRate=240s)│  │
│  │ 线程池: executorSQL (poolSize=1)       │  │
│  │ 任务: 按环境隔离执行 SQL 检测           │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ ExecuteFailedRetry.retry()             │  │
│  │ 触发: @Async + @Scheduled(fixedRate=300s)│  │
│  │ 线程池: retrySQL (poolSize=1)          │  │
│  │ 任务: 重试执行失败的 SQL 文件           │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ GrafanaDataSourceHealthChecker         │  │
│  │ 触发: @Scheduled(fixedRate=300s)       │  │
│  │ 任务: WebClient 检查 Grafana 在线状态   │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ SqlDataSourceHealthChecker             │  │
│  │ 触发: @Scheduled(fixedRate=300s)       │  │
│  │ 任务: DriverManager 直连 SELECT 1      │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ SendWechatService.pushMsg()            │  │
│  │ 触发: @Scheduled(fixedRate=60s)        │  │
│  │ 任务: 每分钟轮询，DB 配置时间补推       │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ ExecutorLogClear.clear()               │  │
│  │ 触发: @Scheduled(cron="0 0 0 * * ?")   │  │
│  │ 任务: 清理 14 天前的推送日志            │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### 5.4 数据流向

```
Grafana Loki ──→ GrafanaLogService ──→ SendDispatchService ──→ SendWechatService
    │                  │                       │                        │
    │ WebClient        │ 关键词匹配            │ 按 webhook 优先级      │ HTTP POST
    │ 连接池+重试      │ 排除关键词             │ 规则级→数据源级→全局    │ 企业微信API
    ▼                  ▼ 上下文截取            ▼ 免打扰检查             ▼
  LogQL查询         异常判断 + 去重         延迟发送处理             推送成功
      │
      └──→ grafana_monitor_rule (last_ts/last_time/采集计数)

SQL 数据源 ──→ SqlExecutorService ──→ SendDispatchService ──→ SendWechatService
    │                  │                       │                        │
    │ JDBC query       │ 有数据=异常           │ 数据源 webhook         │ HTTP POST
    │ HikariCP 连接池  │ 执行规则校验           │ → 全局 webhook 兜底    │ 企业微信API
    ▼                  ▼ 失败重试             ▼                        ▼
 SQL 查询           异常判断 + 计数         免打扰检查                推送成功

Controller ──→ @OperationLogAspect ──→ OperationLogServiceProxy ──→ SQLite
    │                  │                       │                        │
    │ REST API         │ 环绕增强              │ @Async 异步            │ operation_log 表
    ▼                  ▼ IP/UA/参数           ▼                        ▼
 业务处理            字段变更对比            持久化                    存储完成
```

### 5.5 策略模式架构

项目使用三个策略接口实现可扩展性：

```java
// 日志监听 — 可在运行时收集所有实现
interface WatchService { void watchFile(); }
  ├── GrafanaLogServiceImp (远程 Loki)
  └── LocalLogFileServiceImp (本地文件)

// SQL/HTTP 检测 — 扩展时可新增
interface ExecutorService {
    void execute();
    void executeRetry();
    default void executeSingle(String environmentName) { execute(); }
    String getTitle();
}
  └── SqlExecutorService (当前仅有 SQL 实现)

// 消息推送 — 可扩展邮件、短信等
interface SendService {
    void sendMsg(MsgForm msgForm, Consumer<StringBuilder> msg);
    void sendSimpleMarkDownMsgByLog(String content, String environmentName, String webhook);
}
  └── SendWechatService (当前仅有企业微信实现)
```

---

## 6. 核心类详解

### 6.1 ProxyApplication.java（启动类）

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ProxyApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() { return new RestTemplate(); }
}
```

**特点**:
1. `@EnableAsync` 启用异步调度（与 `@EnableScheduling` 配合）
2. 采集时间戳恢复由 `GrafanaLogServiceImp.init()` 中 `loadLastTsFromRules()` 完成（从 `grafana_monitor_rule` 表加载）

### 6.2 GrafanaLogServiceImp.java（Grafana 日志采集）

**特点**:
- 配置从数据库动态加载（通过 `GrafanaDataSource` 表）
- 支持按环境配置 week/startTime/endTime 做时间窗口过滤
- 支持规则级 webhook 和数据源级 webhook
- WebClient 带独立连接池（`grafana-connection-pool`，maxIdleTime=10s）
- 分片拉取：每次最多 500 条，超过则继续拉取下个时间窗口（**无 30 分钟范围截断**）
- 失败重试：最多 2 次，间隔 1s
- `supplement()` 仅用 `@Scheduled`（无 `@Async`，避免重复执行）
- **锁优化**：`webClientMap` 锁内只做 entry 快照，Loki 网络调用放到锁外，避免长时间占锁阻塞 `refreshConfig()`（否则编辑规则保存会卡顿）
- 日志推送 + 持久化 lastTs 到 `grafana_monitor_rule` 表（含采集数量统计）
- `lastTsMap` 取不到时直接查 DB 兜底

**Loki API 调用**:
```
GET {url}/api/datasources/proxy/{dsId}/loki/api/v1/query_range
  ?direction=forward
  &query={query-expr}
  &start={nanosecond timestamp}
  &end={nanosecond timestamp}
  &limit=500
```

### 6.3 SqlExecutorService.java（SQL 执行服务）

**特点**:
- 支持数据源级 webhook（`SqlDataSource.webhook`）
- `executeSingle()` 单环境执行，异常隔离不影响其他环境
- 执行前检查数据源星期/时间段配置
- `executeAble()` 校验：执行窗口、频率、次数限制
- 失败文件重试（`ExecuteFailedRetry` 调用 `executeRetry()`）
- 失败计数每 8 次告警
- 无上限次数文件（`SQLConfig.unLimitCheckFiles`）失败不计次数

### 6.4 SqlConfigService.java（动态连接池管理）

**特点**:
- 从数据库读取数据源配置，调用 `refreshConfig()` 重建
- 使用 `DefaultListableBeanFactory.registerSingleton()` 动态注册 Bean
- 先销毁旧 Bean 再注册新 Bean，避免冲突
- `HikariDataSource` 配置（当前）：
  - `initializationFailTimeout=-1` 忽略启动连接失败
  - `maximumPoolSize=2` 双连接避免单连接被占满
  - `maxLifetime=60s` 短生命周期适应 VPN 环境
  - `idleTimeout=30s` 空闲超时
  - `keepaliveTime=15s` 高频保活避免断连

### 6.5 SendWechatService.java（企业微信推送）

**推送逻辑**:
```java
sendMsg(MsgForm):
  // SQL 异常 → 优先用 MsgForm.webhook（来自数据源）
  // 无 webhook → systemConfigService.getWechatWebhook()（DB）
  // → 再无 → baseConfig.wechatWebhook（YML 兜底）

sendMsgAndStore(msg, msgType, webhook, log):
  // quiet_start ~ quiet_end（DB 配置，默认 20~8）→ sendStatus=false, 补推
  // 其他时间 → POST 企业微信 → sendStatus=true
  // msgType: SQL="text"，日志="markdown"

pushMsg(): // @Scheduled(fixedRate=60s) 轮询
  // 每分钟轮询，push_time（DB，默认 09:30）时执行
  // 每天只跑一次（lastPushDate 防重）
  // 逐条发送，间隔 5s
  // 每条独立保存，互不影响（无 @Transactional）
```

### 6.6 SystemConfigServiceImp.java（系统配置）

**特点**:
- `@PostConstruct` 启动时加载 `system_config` 表到内存缓存
- `configCache`（HashMap）提供 O(1) 读取
- `refreshCache()` 支持热更新
- 提供 `getQuietStartHour()` / `getQuietEndHour()` / `getWechatWebhook()` / `getLogWechatWebhook()` 便捷方法

### 6.7 OperationLogAspect.java（操作日志切面）

**特点**:
- `@Around` 环绕增强，拦截所有标注 `@OperationLog` 的方法
- 自动获取 IP（支持 X-Forwarded-For 等代理头）
- 自动对比修改前后字段，记录变更详情
- 新增字段时新值为 null 跳过（避免未传字段产生干扰变更记录）
- 异步持久化到 `operation_log` 表（不阻塞业务）

### 6.8 ExecuteJDBCContext.java（执行上下文）

**核心职责**:
1. **规则管理**: 缓存各环境的 `sql_execute_rule` 列表
2. **执行判级**: `executeAble()` 判断 SQL 文件是否可执行（窗口/频率/次数）
3. **执行计数**: `executeFileCount()` 内存中记录当日执行次数
4. **失败管理**: `addFailedCount()` / `getFailFiles()` / `clearFailedCount()`
5. **数据源路由**: `jdbcTemplateMap` 维护环境名 → Bean 名的映射

---

## 7. API 接口文档

### 7.1 统计相关 API

#### GET /api/stats/today
获取今日统计数据

**响应**:
```json
{
  "pushTotal": 15,
  "pushStats": { "sql": 10, "log": 5 },
  "sqlStats": {
    "郑州生产": { "totalCount": 5, "failedCount": 1 }
  },
  "date": "2026-08-04"
}
```

#### GET /api/stats/dashboard
获取仪表盘统计数据（SQL/日志推送数按 `msgType` 区分：markdown=日志，其他=SQL）

**响应**:
```json
{
  "todayPushCount": 15,
  "todaySqlExceptionCount": 10,
  "todayLogExceptionCount": 5,
  "onlineDataSourceCount": 8,
  "totalDataSourceCount": 10,
  "onlineLogDataSourceCount": 3,
  "totalLogDataSourceCount": 5,
  "onlineSqlDataSourceCount": 3,
  "totalSqlDataSourceCount": 5,
  "activeRuleCount": 40,
  "last24hExceptionCount": 45
}
```

#### GET /api/stats/environment
获取各环境 SQL 执行统计

#### GET /api/stats/push-by-env
获取各环境推送分类统计（SQL/日志，按 msgType 区分）

#### GET /api/stats/datasource-status
获取所有数据源的在线状态

**响应**:
```json
[
  { "name": "郑州生产", "type": "Grafana", "isOnline": true, "enabled": true, "lastCheckTime": "..." },
  { "name": "郑州生产", "type": "SQL", "isOnline": true, "enabled": true, "lastCheckTime": "..." }
]
```

#### GET /api/stats/log-collect
获取各环境累计和当日日志采集统计（从 grafana_monitor_rule 聚合）

#### GET /api/stats/log-collect/daily
获取各环境当日日志采集统计

### 7.2 SQL 规则管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sql-rules` | 获取所有规则 |
| GET | `/api/sql-rules/{id}` | 获取单条规则 |
| POST | `/api/sql-rules` | 创建规则 |
| PUT | `/api/sql-rules/{id}` | 更新规则 |
| DELETE | `/api/sql-rules/{id}` | 删除规则 |
| GET | `/api/sql-rules/check-unique` | 检查规则唯一性 |
| POST | `/api/sql-rules/copy` | 批量复制规则到新环境（按 环境+文件名 防重，已存在跳过） |

### 7.3 SQL 文件管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sql-files` | 获取文件列表 |
| POST | `/api/sql-files/upload` | 上传 SQL 文件 |
| DELETE | `/api/sql-files/{filename}` | 删除文件 |
| GET | `/api/sql-files/{filename}/content` | 获取文件内容 |
| PUT | `/api/sql-files/{filename}/content` | 更新文件内容 |

### 7.4 SQL 调试 API

#### POST /api/sql-debug/execute

**请求**:
```json
{ "environment": "郑州生产", "sql": "SELECT * FROM orders", "filename": "debug.sql" }
```

**响应**:
```json
{ "success": true, "columns": [...], "rows": [...], "rowCount": 100, "message": "..." }
```

> `filename` 参数用于操作日志记录调试的 SQL 文件名。

### 7.5 数据源管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sql/datasources` | SQL 数据源列表 |
| POST | `/api/sql/datasources` | 创建 SQL 数据源 |
| PUT | `/api/sql/datasources/{id}` | 更新 SQL 数据源 |
| DELETE | `/api/sql/datasources/{id}` | 删除 SQL 数据源 |
| POST | `/api/sql/refresh` | 刷新 SQL 配置 |
| GET | `/api/grafana/datasources` | Grafana 数据源列表 |
| GET | `/api/grafana/datasources/loki-id` | 根据 URL/用户名/密码 自动获取 Loki 数据源 ID（请求 Grafana `/api/datasources`，匹配 type/name 含 loki） |
| POST | `/api/grafana/datasources` | 创建 Grafana 数据源 |
| PUT | `/api/grafana/datasources/{id}` | 更新 Grafana 数据源 |
| DELETE | `/api/grafana/datasources/{id}` | 删除 Grafana 数据源 |
| GET | `/api/grafana/datasources/{id}/rules` | 获取数据源的规则列表 |
| POST | `/api/grafana/rules` | 创建 Grafana 规则 |
| PUT | `/api/grafana/rules/{id}` | 更新 Grafana 规则（**会 refreshConfig() 刷新内存缓存**） |
| DELETE | `/api/grafana/rules/{id}` | 删除 Grafana 规则 |
| POST | `/api/grafana/refresh` | 刷新 Grafana 配置 |
| PUT | `/api/grafana/rules/{id}/last-ts` | 修改采集起始时间（同步更新内存 lastTsMap） |

> 所有增删改操作自动触发 `refreshConfig()` 重建内存缓存。

### 7.6 操作日志 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logs` | 分页查询（支持 type/module/日期范围） |
| GET | `/api/logs/modules` | 获取模块列表 |

### 7.7 环境与配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/environments` | 全部环境列表（Grafana + SQL 并集） |
| GET | `/api/sql/environments` | SQL 数据源环境列表（sql-rules 页用） |
| GET | `/api/system/config` | 系统配置（webhook/免打扰/补推时间） |
| PUT | `/api/system/config` | 更新系统配置 |
| GET | `/api/datasources` | 获取 SQL 数据源名称列表 |
| GET | `/api/push-records` | 推送记录分页查询 |

---

## 8. 数据库设计

### 8.1 SQLite 表结构

项目使用 SQLite 作为本地数据库，存储在 `/soft/sqlite/monitor.db`

#### sql_execute_log（SQL 执行日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| environment_name | TEXT | 环境名称 |
| sql_file_name | TEXT | SQL 文件名 |
| execute_date | INTEGER | 执行日期（yyyyMMdd） |
| count | INTEGER | 执行次数 |
| failed_count | INTEGER | 失败次数 |
| failed_count_reset_time | INTEGER | 失败次数重置时间 |
| create_time | TIMESTAMP | 创建时间 |
| 唯一索引 | idx_env_file_date | (environment_name, sql_file_name, execute_date) |

#### msg_send_log（消息推送日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| content | TEXT | 推送内容 |
| send_webhook | TEXT | 推送目标 webhook |
| msg_type | TEXT | 消息类型（text=SQL/markdown=日志） |
| environment_name | TEXT | 环境名称 |
| create_time | TIMESTAMP | 内容产生时间 |
| send_date | TIMESTAMP | 实际推送时间 |
| send_status | INTEGER | 发送状态（1=已发送 0=未发送） |

#### sql_execute_rule（SQL 执行规则）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| environment_name | TEXT | 环境名称 |
| sql_file_name | TEXT | SQL 文件名 |
| execute_limit | INTEGER | 每日执行上限次数 |
| execute_start_time | TEXT | 每天开始执行时间（HH:mm:ss） |
| execute_end_time | TEXT | 每天停止执行时间（HH:mm:ss） |
| execute_frequency | INTEGER | 执行频率（n 分钟一次） |
| 唯一索引 | idx_env_sql_file | (environment_name, sql_file_name) |

#### sql_data_source（SQL 数据源）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| environment_name | TEXT UNIQUE | 环境名称（唯一） |
| jdbc_url | TEXT | JDBC 连接地址 |
| username | TEXT | 用户名 |
| password | TEXT | 密码（明文存储） |
| driver_class_name | TEXT | 驱动类名 |
| webhook | TEXT | **数据源级 webhook** |
| week | TEXT | 星期配置（JSON 数组） |
| start_time | TEXT | 采集开始时间 |
| end_time | TEXT | 采集结束时间 |
| enabled | INTEGER | 启用状态（1=启用 0=禁用） |
| create_time | TIMESTAMP | 创建时间 |
| update_time | TIMESTAMP | 更新时间 |
| last_check_time | TIMESTAMP | 最后健康检查时间 |
| is_online | INTEGER | 在线状态（1=在线 0=离线） |

#### grafana_data_source（Grafana 数据源）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| url | TEXT | Grafana API 地址 |
| environment_name | TEXT UNIQUE | 环境名称（唯一） |
| datasource_id | TEXT | Loki 数据源 ID |
| username | TEXT | 用户名 |
| password | TEXT | 密码 |
| webhook | TEXT | **数据源级 webhook** |
| week | TEXT | 星期配置（JSON 数组） |
| start_time | TEXT | 采集开始时间 |
| end_time | TEXT | 采集结束时间 |
| enabled | INTEGER | 启用状态 |
| create_time / update_time | TIMESTAMP | 创建/更新时间 |
| last_check_time | TIMESTAMP | 最后健康检查时间 |
| is_online | INTEGER | 在线状态 |

#### grafana_monitor_rule（Grafana 监控规则）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| data_source_id | INTEGER FK | 关联 grafana_data_source |
| name | TEXT | 规则名称 |
| query_expr | TEXT | LogQL 查询表达式 |
| keywords | TEXT | 关键词（JSON 数组） |
| exclusion_keywords | TEXT | 排除关键词（JSON 数组） |
| context_lines | INTEGER | 上下文截取行数（默认 5） |
| webhook | TEXT | **规则级 webhook** |
| enabled | INTEGER | 启用状态 |
| create_time / update_time | TIMESTAMP | 创建/更新时间 |
| last_ts | BIGINT | **最后采集时间戳（毫秒）** |
| last_time | TIMESTAMP | **最后采集时间** |
| total_collect_count | BIGINT | **累计采集日志条数** |
| daily_collect_count | BIGINT | **当日采集日志条数** |
| collect_date | TEXT | **采集日期** |
| 外键 | CASCADE | data_source_id → grafana_data_source(id) |

> 采集时间/统计字段从原 `log_collect_time_info` 表合并而来，schema.sql 中含历史数据迁移 SQL。

#### operation_log（操作日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| ip | TEXT | 客户端 IP |
| user_agent | TEXT | User-Agent |
| operation_type | TEXT | 操作类型（VISIT/CREATE/EDIT/DELETE） |
| module | TEXT | 模块名称 |
| target_id | INTEGER | 操作目标 ID |
| detail | TEXT | 详情（含变更对比） |
| create_time | TIMESTAMP | 操作时间 |

#### system_config（系统配置）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| config_key | TEXT UNIQUE | 配置键 |
| config_value | TEXT | 配置值 |
| updated_at | TIMESTAMP | 更新时间 |

> 保留 `log_collect_time_info` 表结构（CREATE TABLE IF NOT EXISTS），但已不再使用，数据已迁移到 `grafana_monitor_rule`。

---

## 9. 配置文件详解

> 参考 `application.yml` 实际内容，涵盖所有配置项。注意 *全局 webhook、免打扰时段、补推时间已迁移到 `system_config` 表*，YML 仅用于首次启动的默认值和兜底。

### 9.1 核心配置结构

| 配置路径 | 作用 | 现管理方式 |
|----------|------|-----------|
| `server.port` | 应用端口 4000 | YML |
| `spring.datasource` | SQLite 本地数据库 | YML |
| `mybatis-plus` | MyBatis 配置 | YML |
| `management` | Actuator/Prometheus 监控端点 | YML |
| `watcher.notify-webhook` | 全局 webhook（SQL/日志） | **system_config 表**（YML 兜底） |
| `watcher.sql.*` | SQL 数据源配置 | **已迁移到 DB** |
| `watcher.log.grafana.list` | Grafana 数据源配置 | **已迁移到 DB** |
| `watcher.log.local` | 本地日志配置 | YML（已禁用） |

### 9.2 配置优先级

1. 数据库动态配置（SQL/Grafana 数据源、规则、system_config）
2. 命令行参数 `-Dxxx=yyy`
3. `application.yml` 全局默认值（webhook 兜底）

---

## 10. 定时任务系统

### 10.1 线程池配置

定时任务线程池在 `ScheduleConfig` 中定义（均为 `ThreadPoolTaskScheduler`）：

| 线程池名 | poolSize | 用途 |
|----------|----------|------|
| `executorSQL` | 1 | SQL 执行调度 |
| `retrySQL` | 1 | SQL 重试 |
| `grafanaLog` | 1 | Grafana 日志采集 |

### 10.2 任务列表

| 任务名 | 触发机制 | 功能说明 | 备注 |
|--------|----------|----------|------|
| GrafanaLogServiceImp.supplement() | `@Scheduled(fixedRate=30s)` | Grafana Loki 日志采集 | 无 @Async，独立连接池，锁内快照锁外网络调用 |
| ExecutorScheduler.executor() | `@Scheduled(fixedRate=240s)` | SQL 执行检测 | 按环境隔离 |
| ExecuteFailedRetry.retry() | `@Scheduled(fixedRate=300s)` | 重试失败的 SQL | 每 5 分钟 |
| GrafanaDataSourceHealthChecker.checkHealth() | `@Scheduled(fixedRate=300s)` | Grafana 健康检查 | WebClient 重试，看响应是否 200 |
| SqlDataSourceHealthChecker.checkHealth() | `@Scheduled(fixedRate=300s)` | SQL 健康检查 | DriverManager 直连 |
| SendWechatService.pushMsg() | `@Scheduled(fixedRate=60s)` | 补推免打扰消息 | 每分钟轮询，时间由 DB 配置 |
| ExecutorLogClear.clear() | `0 0 0 * * ?` | 清理 14 天前日志 | 每天凌晨 |

---

## 11. 开发历史（Git Log）

### 11.1 最近提交记录

```
2026-08-31 1eac01c fix: Grafana健康检查不依赖响应体非空判断在线
2026-08-31 5025c22 fix: supplement网络调用移出webClientMap锁，避免阻塞refreshConfig
2026-08-17 2415d43 fix: 新增监控规则弹窗flatpickr的dateFormat修正为H:i:s
2026-08-17 60c9c69 fix: 编辑数据源时间为空时默认填充08:00和20:00
2026-08-17 dd92eff fix: 新增数据源时开始/结束时间默认填充08:00和20:00
2026-08-17 e5ac9a4 feat: 数据源开始/结束时间改用原生time选择器
2026-08-17 a31af07 feat: 星期配置改为圆形多选组件，替代逗号分隔输入
2026-08-17 9266c0d fix: 保留完整错误信息，仅清理Grafana网关的<EOL>分隔标记
2026-08-13 43bf144 fix: 错误图标title移到外层span，悬停可正常显示错误文本
2026-08-13 53b8f1b fix: 数据源ID加载中spinner用innerHTML渲染，恢复失败title提示
2026-08-13 19fd307 fix: 重新打开数据源弹窗时重置ID状态，去掉残留红边和红色图标
2026-08-13 6d3f06a feat: Grafana数据源ID自动获取，无需手动输入
2026-08-13 498a845 feat: SQL规则管理支持批量复制规则到新环境
2026-08-13 7b833c5 fix: 修复push-records分页不生效
2026-08-04 5583db9 fix: SQL规则管理页面只加载SQL数据源环境，新增/api/sql/environments
2026-08-04 1143a17 fix: /api/environments合并SQL数据源环境名
2026-07-21 0a35098 fix: 修改监控规则后刷新GrafanaLogServiceImp内存缓存
2026-07-02 2daaa07 fix: 增加HikariCP连接池大小和keepalive频率，避免SQL执行因断连超时
2026-07-02 fe6b35e fix: SQL数据源健康检查改用直连，不与执行器争用HikariCP连接池
2026-07-02 0fdcc67 fix: 首页推送统计按msgType区分SQL/日志，不再按内容关键词匹配
2026-06-25 49cab1a fix: processMonitor取不到lastTs时直接查DB兜底
2026-06-25 7eaf8a2 fix: 移除supplement()的@Async避免重复执行
2026-06-25 49fc771 refactor: 移除processMonitor的30分钟时间范围限制
2026-06-25 22872de fix: 修改采集起始时间后刷新GrafanaLogServiceImp的lastTsMap
2026-06-08 a25d507 fix: 移除@Transactional避免SQLite锁竞争，按环境隔离异常
2026-06-08 87c40f7 fix: ExecutorScheduler按环境隔离异常，失败不影响其他环境
2026-06-06 40d4150 refactor: log_collect_time_info合并到grafana_monitor_rule
2026-06-05 408021f fix: 缩短连接池maxIdleTime至10s+evictInBackground
2026-06-05 613b863 feat: 补推时间改为DB配置 + @Scheduled固定频率轮询
2026-06-05 67f4f6b feat: 全局webhook和免打扰时段配置写入SQLite，提供system-config.html
2026-06-05 e788ab0 fix: sql.init.mode改为never，避免每次启动重新执行datainit.sql
2026-06-04 68345a7 feat: SQL数据源推送支持数据源级别webhook
2026-06-04 bd16ff9 fix: 首次启动时CommandLineRunner导入数据后未刷新数据源配置
2026-06-04 25126e5 feat: 操作日志日期选择改为flatpickr日期范围组件
```

### 11.2 功能演进历程

#### 第一阶段：基础功能（早期）
- Grafana 日志监听
- SQL 执行检测
- 企业微信推送

#### 第二阶段：架构优化（2026-05-29 ~ 05-30）
- **SQL 数据源动态管理**: YML 配置 → 数据库管理
- **Grafana 数据源动态管理**: YML 配置 → 数据库管理
- **健康检查**: 独立定时器 + WebClient 连接池 + 重试
- **初始化器**: SqlDataInitializer + GrafanaDataInitializer 首次导入
- **Reactor 版本冲突**: 统一版本修复

#### 第三阶段：前端增强（2026-05-30 ~ 06-01）
- **首页改版**: 卡片式仪表盘、数据源状态、日志采集统计
- **操作日志页面**: flatpickr 日期范围选择、快捷选项
- **推送记录分页**: 分页查询
- **统一导航**: 所有页面一致侧边栏

#### 第四阶段：操作日志系统（2026-05-31 ~ 06-01）
- **注解+切面**: AOP 记录所有操作
- **字段变更对比**: 支持 4 类实体比对
- **空值跳过**: 未传字段不参与对比
- **分页插件**: MyBatis-Plus PaginationInnerInterceptor

#### 第五阶段：质量加固（2026-06-04 ~ 06-05）
- **Webhook 数据源级**: SQL/Grafana 支持独立 webhook
- **补推事务修复**: 移除 @Transactional，避免部分失败回滚全部
- **初始化顺序修复**: CommandLineRunner 导入后刷新配置
- **连接池/重试**: 全链路连接池 + 重试机制

#### 第六阶段：系统配置与调度优化（2026-06-05 ~ 06-08）
- **system_config 表**: 全局 webhook/免打扰/补推时间写入 SQLite
- **补推时间动态化**: @Scheduled(cron) → fixedRate 轮询 + DB 配置
- **SQLite 锁竞争修复**: ExecutorScheduler 移除 @Transactional，按环境隔离
- **执行器环境隔离**: executeSingle() 单环境执行，异常隔离
- **唯一约束冲突**: addFailFiles 逐条 save 替代批量 saveOrUpdateBatch

#### 第七阶段：采集时间整合（2026-06-06）
- **log_collect_time_info 合并**: 采集进度字段整合进 grafana_monitor_rule
- **采集时间页面配置**: 规则编辑弹窗内嵌 flatpickr 采集起始时间
- **首页采集统计**: 改为从 grafana_monitor_rule 聚合

#### 第八阶段：稳定性修复（2026-06-25 ~ 08-04）
- **supplement 去重**: 移除 @Async 避免重复执行
- **lastTs 兜底**: processMonitor 取不到缓存直接查 DB
- **推送统计修正**: 首页按 msgType 区分 SQL/日志推送
- **健康检查直连**: SQL 数据源改用 DriverManager 直连
- **连接池调优**: maxPoolSize=2, keepalive=15s, maxLifetime=60s
- **规则缓存刷新**: 修改监控规则后 refreshConfig()
- **环境接口拆分**: /api/sql/environments 只返回 SQL 数据源环境

#### 第九阶段：前端体验优化（2026-08-04 ~ 08-17）
- **分页修复**: push-records 分页不生效（自定义 SqlSessionFactory 未挂载分页插件）
- **批量复制规则**: sql-rules 支持从一个环境复制全部规则到新环境（防重）
- **数据源ID自动获取**: 填 URL/用户名/密码后自动查 Grafana Loki id，无需手动输入
- **星期圆形多选**: sql-config/grafana-config 星期改为圆圈多选组件
- **时间选择器**: 开始/结束时间改用原生 time 组件，空值默认 08:00/20:00
- **规则保存修复**: flatpickr dateFormat 修正，采集起始时间可正常保存

#### 第十阶段：锁竞争与在线判定（2026-08-17 ~ 08-31）
- **supplement 锁优化**: 网络调用移出 webClientMap 锁，避免阻塞 refreshConfig
- **Grafana 在线判定修复**: /api/org 返回空 body 时不误判离线（HTTP 200 即在线）

### 11.3 关键技术决策

| 决策 | 说明 |
|------|------|
| SQLite vs MySQL | 选择 SQLite 作为本地存储，简化部署（无需单独数据库） |
| MyBatis-Plus | 简化 CRUD 操作 + 分页插件（需显式挂载到自定义 SqlSessionFactory） |
| WebFlux Reactor 版本锁定 | 统一 3.4.34/1.0.39 避免 webflux 冲突 |
| 健康检查直连 | SQL 数据源用 DriverManager 直连，避免与执行器争用连接池 |
| Grafana 在线判定 | HTTP 200 即在线，不依赖响应体非空（部分 Grafana 返回空 body） |
| supplement 锁优化 | 锁内快照锁外网络调用，避免长时间占锁阻塞 refreshConfig |
| 健康检查频率 5 分钟 | 平衡时效性和网络开销，失败重试 2 次 |
| 补推时间动态化 | fixedRate 轮询 + DB 配置，免重启动态生效 |
| 采集进度整合 | log_collect_time_info 合并到 grafana_monitor_rule，消除冗余 |
| 配置管理 | 首次启动 YML 导入 DB，后续通过 Web 页面动态管理 |
| 数据源级 webhook | 不同环境异常可推送到不同企业微信群 |
| 补推无事务 | 每条消息独立保存，一条失败不影响其他消息 |
| 按环境隔离 | executeSingle() 单环境执行，一个环境失败不影响其他环境 |
| 数据源ID自动获取 | 前端填 URL/用户名/密码后自动查 Grafana Loki id，避免手动输入出错 |

---

## 12. 部署流程

### 12.1 环境要求

| 环境 | 要求 |
|------|------|
| Java | 1.8+ |
| Maven | 3.6+ |
| 服务器 | Linux (CentOS/Ubuntu) |
| 磁盘 | 至少 1GB 可用空间 |

### 12.2 部署步骤（对应 DEVELOPMENT_FLOW.md）

#### 步骤 1：编译检查
```bash
cd Z:\monitor
mvn compile -D"maven.test.skip"=true
```

#### 步骤 2：提交代码
```bash
git add .
git commit -m "提交描述"
git push origin f_claude
```

#### 步骤 3：拉取项目
```bash
cd D:\SZH\projects\AI\monitor
git pull origin f_claude
```

#### 步骤 4：构建打包
```bash
mvn clean install -D"maven.test.skip"=true
# 产物: target/actuator.jar
```

#### 步骤 5：上传到服务器
```bash
scp target/actuator.jar root@192.168.199.85:/soft/actuator/
```

#### 步骤 6：重启服务
```bash
ssh root@192.168.199.85 "systemctl stop actuator"
ssh root@192.168.199.85 "systemctl start actuator"
```
> 注意：若服务卡在 stop 状态，可用 `kill -9 <pid>` 强杀后重启，必要时 `systemctl reset-failed actuator`。

#### 步骤 7：检查状态
```bash
ssh root@192.168.199.85 "systemctl status actuator"
# 日志路径: /soft/actuator/app.log
```

### 12.3 常用运维命令

```bash
# 查看应用状态
ps -ef | grep actuator.jar
systemctl status actuator

# 查看日志
tail -f /soft/actuator/app.log
tail -n 100 /soft/actuator/app.log
grep ERROR /soft/actuator/app.log

# 健康检查
curl http://localhost:18081/actuator/health
curl http://localhost:18081/actuator/prometheus

# 端口占用
netstat -tlnp | grep -E '4000|18081'
```

---

## 13. 开发注意事项

### 13.1 代码规范

1. **命名规范**:
   - 类名：UpperCamelCase（`SqlExecutorService`）
   - 方法名：lowerCamelCase（`executeSqlFiles`）
   - 常量：UPPER_SNAKE_CASE（`MAX_RETRY_COUNT`）

2. **Git 提交规范**:
   ```
   feat: 新功能
   fix: 缺陷修复
   refactor: 重构
   perf: 性能优化
   chore: 构建/工具相关
   docs: 文档更新
   ```

3. **异常处理**:
   ```java
   // ✅ 推荐
   try { doSomething(); }
   catch (SpecificException e) {
       logger.error("业务描述：{}", e.getMessage(), e);
   }
   // ❌ 避免
   try { doSomething(); }
   catch (Exception e) { e.printStackTrace(); }
   ```

### 13.2 常见陷阱

1. **WebFlux Reactor 版本冲突** — pom.xml 中排除默认版本，统一 3.4.34
2. **SQLite 路径** — 服务器上需创建 `/soft/sqlite` 并确保读写权限
3. **HikariCP 属性名** — 使用 `maximum-pool-size` 而非 `maxPoolSize`
4. **@Scheduled + @Async 叠加** — 会导致同一方法重复执行，需避免
5. **离线数据源处理** — 离线数据源不执行任务，避免无效操作
6. **首次启动初始化顺序** — `SqlDataInitializer`/`GrafanaDataInitializer` 导入数据后必须调用 `refreshConfig()`
7. **操作日志空值** — 更新接口只传部分字段时，**未传字段新值为 null，已被切面跳过不记录**
8. **SQL 数据源 webhook** — 非 SQL 异常走 `baseConfig.wechatWebhook`，SQL 异常优先走数据源级 webhook
9. **switch fall-through** — `sendMsgAndStore()` 中的 switch 已改为 if/else，避免 text 穿透到 markdown
10. **连接池争用** — 健康检查不要用执行器的 JdbcTemplate，会因连接被占满导致误判离线
11. **SQLite 锁竞争** — 涉及 SQLite 长事务的操作避免加 @Transactional，按环境隔离
12. **分页插件挂载** — 自定义 `MybatisSqlSessionFactoryBean` 必须 `setPlugins(mybatisPlusInterceptor)`，否则 selectPage 返回全量
13. **flatpickr dateFormat** — `H:i:ss` 会把 ss 解析为两个秒，应写 `H:i:s`
14. **FA 图标 title** — `::before` 伪元素不响应 hover，title 需放到外层元素上
15. **supplement 锁** — 网络调用不要放 `synchronized(webClientMap)` 锁内，会阻塞 refreshConfig
16. **Grafana 空 body** — `/api/org` 可能返回 HTTP 200 但空 body，在线判断不要用 `response.isEmpty()`

### 13.3 性能优化

1. **健康检查频率**: 5 分钟 + 2 次重试
2. **连接池**: Grafana 独立连接池，SQL 动态 HikariCP（maxPoolSize=2）
3. **SQL 执行限流**: 每个数据源最多 2 连接
4. **日志级别**: 生产环境 INFO，`com.szh.monitor.service.impl` DEBUG
5. **线程池隔离**: 日志采集/SQL执行/重试各自独立

---

## 14. 常见问题与解决

### Q1: 编译报错 "package javax.servlet does not exist"
**解决**: Spring Boot 2.7.x 使用 `javax.servlet`，勿引入 `jakarta.servlet`

### Q2: WebFlux Reactor 版本冲突
**解决**: pom.xml 排除冲突依赖，统一 reactor-core 3.4.34

### Q3: 数据源离线但实际可连
**可能原因**:
1. 健康检查与执行器争用 HikariCP 连接池（maxPoolSize=1 时易发生）→ 已改为直连
2. 健康检查时 Bean 不存在 → 检查 `SqlConfigService.refreshConfig()` 是否被调用

### Q4: HikariCP 连接超时
**解决**:
```yaml
maximum-pool-size: 2
connection-timeout: 10000
max-lifetime: 60000
keepalive-time: 15000
```

### Q5: 企业微信推送失败
**排查**:
1. 检查 webhook URL 是否正确（全局在 system_config 表，数据源级在数据库）
2. Webhook 优先级：规则级 → 数据源级 → 全局

### Q6: 补推消息重复推送
**历史原因**: 旧版 `@Transactional` 导致部分失败回滚全部状态。**已修复**：移除事务，每条独立保存。

### Q7: 操作日志出现"在线状态 修改前 是 修改后 -"
**历史原因**: 字段对比时没有跳过 null 新值。**已修复**：新值为 null 跳过不记录。

### Q8: 修改规则（排除关键词）不生效
**历史原因**: 编辑规则只更新 DB，`processMonitor()` 用的是内存缓存。**已修复**：`updateRule()` 更新后调 `refreshConfig()` 刷新缓存。

### Q9: 修改采集起始时间后不生效
**历史原因**: `PUT /api/grafana/rules/{id}/last-ts` 只更新 DB，内存 `lastTsMap` 未刷新。**已修复**：Controller 更新后直接 `initLastTsMap()` 写入内存。

### Q10: 首页 SQL 异常推统计包含了日志推送
**历史原因**: 用 `content.contains("sql")` 匹配，日志内容也可能含 "sql"。**已修复**：按 `msgType` 区分（markdown=日志，text=SQL）。

### Q11: 日志采集同一时间区间重复执行
**历史原因**: `@Scheduled + @Async` 叠加导致同一方法执行两次。**已修复**：移除 `@Async`。

### Q12: push-records 翻页没效果
**历史原因**: 自定义 SqlSessionFactory 未挂载分页插件，selectPage 返回全量。**已修复**：`MybatisSqlSessionFactoryBean.setPlugins(mybatisPlusInterceptor)`。

### Q13: 编辑规则保存采集起始时间卡很久才报保存失败
**历史原因**: supplement() 在 webClientMap 锁内做网络调用，长时间占锁阻塞 refreshConfig。**已修复**：锁内只做快照，网络调用移到锁外。

### Q14: 网络通但 Grafana 数据源显示离线
**历史原因**: 郑州等 Grafana 的 `/api/org` 返回 HTTP 200 但空 body，原逻辑 `response.isEmpty()` 误判离线。**已修复**：`isOnline = response != null`，HTTP 200 即在线。

### Q15: 编辑规则时采集起始时间保存失败
**历史原因**: flatpickr dateFormat `H:i:ss` 导致秒重复输出（如 `10:00:0013`），后端解析失败。**已修复**：改为 `H:i:s`。

---

## 15. 项目规范

### 15.1 参考文档

- [DEVELOPMENT_FLOW.md](DEVELOPMENT_FLOW.md) — 研发过程约定（含完整部署流程）
- [README.md](README.md) — 项目快速入门

### 15.2 发布流程

```
代码冻结 → 创建分支 → 完整测试 → 构建发布包
    ↓
部署测试环境 → 验证测试 → 部署预生产 → 最终验证
    ↓
部署生产环境 → 监控观察 → 发布完成
```

### 15.3 回滚方案

| 场景 | 回滚条件 | 回滚操作 |
|------|----------|----------|
| 功能异常 | 监控指标异常 | `systemctl stop` → 替换 JAR → `systemctl start` |
| 数据异常 | 数据一致性问题 | 回滚数据库 + 应用 |

### 15.4 监控告警

- **Prometheus**: `http://192.168.199.85:18081/actuator/prometheus`
- **健康检查**: `http://192.168.199.85:18081/actuator/health`
- **应用日志**: `/soft/actuator/app.log`

---

**文档版本**: v4.0  
**最后更新**: 2026-08-31  
**更新内容**: 同步至 v2026-08-31 最新代码。新增 SQL 规则批量复制、Grafana 数据源 ID 自动获取、星期圆形多选、时间选择器组件、supplement 锁优化（网络调用移出锁）、Grafana 空 body 误判离线修复、分页插件挂载修复、flatpickr dateFormat 修复等。
