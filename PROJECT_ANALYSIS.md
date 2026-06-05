# 监控系统项目分析文档

> **文档目的**: 帮助 AI 模型或其他开发人员快速理解项目架构、代码逻辑和开发流程  
> **项目类型**: Spring Boot 后端监控系统  
> **最后更新**: 2026-06-05

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
│  │ 仪表盘首页   │ → 卡片式展示关键指标 + flatpickr 日期 │
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
| SQLite | 3.45.1.0 | 本地持久化：数据源配置、规则、推送日志、操作日志、采集统计 |
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
    │   │   │                             # - CommandLineRunner 初始化日志监听
    │   │   │
    │   │   ├── annotation/               # 注解定义
    │   │   │   └── OperationLog.java     # 操作日志注解（@OperationLog）
    │   │   │
    │   │   ├── aspect/                   # AOP 切面
    │   │   │   ├── OperationLogAspect.java    # ⭐ 操作日志核心切面
    │   │   │   └── OperationLogServiceProxy.java # 异步日志代理
    │   │   │
    │   │   ├── config/                   # 配置类（17 个）
    │   │   │   ├── BaseConfig.java       # 企业微信 Webhook 配置
    │   │   │   ├── GrafanaConfig.java    # Grafana Loki YML 映射
    │   │   │   ├── LocalLogConfig.java   # 本地日志配置
    │   │   │   ├── MonitorRules.java     # 监控规则配置映射
    │   │   │   ├── SQLConfig.java        # SQL 执行配置
    │   │   │   ├── MultiDataSourceConfig.java  # SQL 多数据源 YML 映射
    │   │   │   ├── SQLiteDataSourceConfig.java # SQLite 主数据源
    │   │   │   ├── MybatisPlusConfig.java      # MyBatis Plus 分页插件
    │   │   │   ├── ScheduleConfig.java         # @Async 线程池配置
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
    │   │   │   ├── MonitorController.java    # ⭐ 核心 API：统计/规则/SQL文件/调试
    │   │   │   ├── GrafanaController.java    # Grafana 数据源+规则 CRUD
    │   │   │   ├── SqlController.java        # SQL 数据源 CRUD
    │   │   │   └── OperationLogController.java # 操作日志查询
    │   │   │
    │   │   ├── entity/                  # 数据库实体（8 个）
    │   │   │   ├── GrafanaDataSource.java    # Grafana 数据源
    │   │   │   ├── GrafanaMonitorRule.java   # Grafana 监控规则
    │   │   │   ├── SqlDataSource.java        # SQL 数据源
    │   │   │   ├── SqlExecuteLog.java        # SQL 执行日志
    │   │   │   ├── SqlExecuteRule.java       # SQL 执行规则
    │   │   │   ├── MsgSendLog.java           # 消息推送日志
    │   │   │   ├── OperationLog.java         # 操作日志
    │   │   │   └── LogCollectTimeInfo.java   # 日志采集时间/统计
    │   │   │
    │   │   ├── mapper/                  # MyBatis Mapper（8 个）
    │   │   │   ├── GrafanaDataSourceMapper.java
    │   │   │   ├── GrafanaMonitorRuleMapper.java
    │   │   │   ├── SqlDataSourceMapper.java
    │   │   │   ├── SqlExecuteLogMapper.java
    │   │   │   ├── SqlExecuteRuleMapper.java
    │   │   │   ├── MsgSendLogMapper.java
    │   │   │   ├── OperationLogMapper.java
    │   │   │   └── LogCollectTimeInfoMapper.java
    │   │   │
    │   │   ├── service/                 # 服务接口
    │   │   │   ├── WatchService.java         # 日志监听
    │   │   │   ├── ExecutorService.java      # SQL 执行
    │   │   │   ├── SendService.java          # 消息推送
    │   │   │   ├── SqlDataSourceService.java
    │   │   │   ├── GrafanaDataSourceService.java
    │   │   │   ├── GrafanaMonitorRuleService.java
    │   │   │   ├── SqlExecuteLogService.java
    │   │   │   ├── SqlExecuteRuleService.java
    │   │   │   ├── MsgSendLogService.java
    │   │   │   ├── OperationLogService.java
    │   │   │   └── LogCollectTimeInfoService.java
    │   │   │
    │   │   ├── service/impl/            # 服务实现（15 个）
    │   │   │   ├── DispatchLogService.java       # 日志监听分发（启动时创建线程）
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
    │   │   │   └── LogCollectTimeInfoServiceImp.java
    │   │   │
    │   │   ├── scheduled/               # 定时任务（5 个）
    │   │   │   ├── ExecutorScheduler.java         # SQL 执行调度（4分钟）
    │   │   │   ├── ExecuteFailedRetry.java        # 失败重试（5分钟）
    │   │   │   ├── ExecutorLogClear.java          # 日志清理（每天凌晨）
    │   │   │   ├── GrafanaDataSourceHealthChecker.java # Grafana 健康检查（5分钟）
    │   │   │   └── SqlDataSourceHealthChecker.java    # SQL 健康检查（5分钟）
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
    │   │   ├── db/schema.sql           # SQLite 表结构
    │   │   ├── db/datainit.sql         # 初始数据
    │   │   └── static/                 # 前端静态页面（7 个）
    │   │       ├── index.html          # ⭐ 仪表盘首页
    │   │       ├── grafana-config.html # Grafana 数据源配置
    │   │       ├── sql-config.html     # SQL 数据源配置
    │   │       ├── sql-rules.html      # SQL 执行规则管理
    │   │       ├── sql-upload.html     # SQL 文件上传/编辑
    │   │       ├── push-records.html   # 推送记录查询
    │   │       └── operation-logs.html # 操作日志查询（flatpickr 日期范围）
    │   │
    │   └── resources/mapper/           # MyBatis XML（部分用注解替代）
    │
    └── test/java/...                  # 测试代码
```

### 3.2 核心文件说明

| 文件路径 | 重要性 | 说明 |
|----------|--------|------|
| `ProxyApplication.java` | ⭐⭐⭐ | 启动类，启用 @Async/@Scheduling，初始化日志监听 |
| `application.yml` | ⭐⭐⭐ | 配置文件：端口、数据源、监控规则、webhook |
| `pom.xml` | ⭐⭐ | Maven 配置，含 Reactor 版本锁定 |
| `GrafanaLogServiceImp.java` | ⭐⭐⭐ | Grafana Loki 日志采集（30s 间隔，连接池+重试） |
| `SqlExecutorService.java` | ⭐⭐⭐ | SQL 执行检测，支持数据源级 webhook |
| `SendWechatService.java` | ⭐⭐⭐ | 企业微信推送，免打扰+补推 |
| `SqlConfigService.java` | ⭐⭐⭐ | 动态连接池管理，Bean 注册 |
| `OperationLogAspect.java` | ⭐⭐ | 操作日志 AOP 切面，字段变更对比 |
| `ExecuteJDBCContext.java` | ⭐⭐ | SQL 执行规则校验 + 执行计数 + 失败管理 |
| `GrafanaDataSourceHealthChecker.java` | ⭐⭐ | Grafana 健康检查（WebClient + 连接池 + 重试） |
| `SqlDataSourceHealthChecker.java` | ⭐⭐ | SQL 健康检查（JDBC SELECT 1） |
| `MonitorController.java` | ⭐⭐ | 核心 API：统计、规则、文件、调试 |

---

## 4. 核心功能模块

### 4.1 日志监听模块

**职责**: 监听 Grafana Loki 和本地日志文件，检测错误并推送告警

**核心类**: 
- `GrafanaLogServiceImp.java` - Grafana Loki 远程日志（主力）
- `LocalLogFileServiceImp.java` - 本地日志文件

**Grafana 日志采集流程**:
```
1. @Scheduled(fixedRate=30s) 定时触发 supplement()
2. 遍历启用且在线的数据源 → 遍历各监控规则
3. 检查星期/时间段约束
4. 从 lastTsMap 获取最后采集时间戳
5. 按 30 分钟切片调用 Loki API（/api/datasources/proxy/{dsId}/loki/api/v1/query_range）
6. WebClient 连接池（maxConnections=10, timeout=120s）
7. 失败自动重试（最多 2 次，间隔 1s）
8. 解析返回的 streams → values
9. 关键词匹配（需包含关键字）→ 排除关键词过滤
10. 截取上下文（contextLines 行）
11. 去重：按 lastTsMap 跳过已处理的时间戳
12. 推送：优先使用规则级 webhook → 数据源级 webhook
13. 更新 lastTsMap 和 LogCollectTimeInfo（持久化）
```

**关键配置**: `GrafanaConfig` / `GrafanaDataSource` 表

### 4.2 SQL 检测模块

**职责**: 定时执行 SQL 脚本，检测业务异常并推送告警

**核心类**:
- `SqlExecutorService.java` - SQL 执行逻辑
- `ExecutorScheduler.java` - 定时调度（每 4 分钟）
- `ExecuteFailedRetry.java` - 失败重试（每 5 分钟）
- `ExecuteJDBCContext.java` - 执行规则校验 + 计数 + 失败管理

**功能流程**:
```
1. ExecutorScheduler 加载各环境的执行规则到 ExecuteJDBCContext
2. 遍历启用在线的数据源
3. 扫描 SQL 目录（/soft/monitor）获取 .sql 文件列表
4. 过滤：只执行有规则的 SQL 文件
5. 对每个文件：
   a. executeAble() 检查 — 执行窗口/次数限制/频率
   b. 执行 SQL 查询
   c. 查询结果有数据 = 异常 → 推送告警
   d. 记录执行日志
6. 执行失败的文件：
   - 非"无上限次数"文件 → 记录失败计数
   - SQLExecutorFailException 抛出 → 失败计数递增
   - 每 8 次失败推送一次告警
7. 全部成功 → 清空失败计数
```

**关键配置**: `SQLConfig` / `sql_execute_rule` 表

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
  //          → 无则使用 baseConfig.wechatWebhook（全局兜底）
  // Grafana 场景 → 优先使用规则 webhook
  //              → 无则使用数据源 webhook
  //              → 再无则使用 baseConfig.logWechatWebhook

sendMsgAndStore(msg, msgType, webhook, log):
  // 免打扰：20:00 ~ 08:00 → sendStatus=false，明天 09:30 补推
  // 非免打扰：POST 企业微信 → sendStatus=true
  // 持久化到 msg_send_log 表
```

**补推流程**:
```java
@Scheduled(cron = "0 30 9 * * ?")  // 每天早上 9:30
pushMsg():
  // 查询所有 sendStatus=false 的记录
  // 每条间隔 5 秒发送
  // 每条独立保存，失败只跳过单条（无 @Transactional）
```

### 4.4 Web 管理界面

**前端技术**: 原生 HTML + Tailwind CSS + Font Awesome

**页面列表**:

| 页面 | 功能 | 新增特性 |
|------|------|----------|
| `index.html` | 仪表盘首页 | 关键指标卡片（SQL/日志异常数）、数据源在线状态、日志采集统计、图表 |
| `grafana-config.html` | Grafana 数据源/规则 CRUD | 动态管理，支持 webhook/星期/时段配置 |
| `sql-config.html` | SQL 数据源 CRUD | 动态管理，支持 webhook 配置 |
| `sql-rules.html` | SQL 执行规则管理 | 执行频率/次数/时间段配置 |
| `sql-upload.html` | SQL 文件上传/在线编辑 | 内容编辑、文件删除 |
| `push-records.html` | 推送记录查询 | 分页查询 |
| `operation-logs.html` | 操作日志 | flatpickr 日期范围组件、快捷选项、类型/模块筛选、详情弹窗 |

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

**职责**: 定时检查数据源在线状态

**Grafana 健康检查** (`GrafanaDataSourceHealthChecker`):
- 每 5 分钟检查一次
- 使用 WebClient 调用 `/api/org` API
- 独立连接池（health-check-pool）
- 最多重试 2 次

**SQL 健康检查** (`SqlDataSourceHealthChecker`):
- 每 5 分钟检查一次
- 通过动态注册的 JdbcTemplate 执行 `SELECT 1`
- Bean 不存在时标记离线

---

## 5. 架构设计说明

### 5.1 分层架构

```
┌──────────────────────────────────────────────┐
│         Web 层 (Controllers)                  │
│  MonitorController, GrafanaController,       │
│  SqlController, OperationLogController        │
├──────────────────┬───────────────────────────┤
│                  │ @OperationLog AOP 切面     │
├──────────────────┴───────────────────────────┤
│           Service 层 (Services)               │
│  GrafanaLogService, SqlExecutorService,       │
│  SendWechatService, SqlConfigService          │
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
  │           └─ 构建监控规则内存缓存
  │
  ├─→ CommandLineRunner.run()
  │     ├─ dispatchLogService.startWatching()   ← 启动 WatchService 线程
  │     └─ logCollectTimeInfoService.initLastTSMAP() ← 恢复采集时间戳
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
│       @EnableScheduling + @Async 线程池        │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │ GrafanaLogServiceImp.supplement()      │  │
│  │ 触发: @Async + @Scheduled(fixedRate=30s)│  │
│  │ 线程池: grafanaLog (2 core threads)    │  │
│  │ 任务: 遍历所有 Grafana 数据源/规则采集日志 │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ ExecutorScheduler.executor()           │  │
│  │ 触发: @Async + @Scheduled(fixedRate=240s)│  │
│  │ 线程池: executorSQL (2 core threads)   │  │
│  │ 任务: 加载规则 → 执行 SQL 检测          │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ ExecuteFailedRetry.retry()             │  │
│  │ 触发: @Async + @Scheduled(fixedRate=300s)│  │
│  │ 线程池: retrySQL (2 core threads)      │  │
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
│  │ 任务: JDBC SELECT 1 检查 SQL 在线状态   │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │ SendWechatService.pushMsg()            │  │
│  │ 触发: @Scheduled(cron="0 30 9 * * ?")  │  │
│  │ 任务: 补推免打扰时段的延迟消息          │  │
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
public class ProxyApplication implements CommandLineRunner {

    @Autowired @Lazy
    private DispatchLogService dispatchLogService;
    @Autowired
    private LogCollectTimeInfoService logCollectTimeInfoService;

    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() { return new RestTemplate(); }

    @Override
    public void run(String... args) {
        dispatchLogService.startWatching();   // 启动日志监听线程
        logCollectTimeInfoService.initLastTSMAP(); // 恢复采集时间戳
    }
}
```

**特点**:
1. `@EnableAsync` 启用异步调度（与 `@EnableScheduling` 配合）
2. `@Lazy` 注入 `DispatchLogService`，避免循环依赖
3. 启动时恢复持久化的 `lastTsMap`，确保重启不丢日志

### 6.2 GrafanaLogServiceImp.java（Grafana 日志采集）

**特点**:
- 配置从数据库动态加载（通过 `GrafanaDataSource` 表）
- 支持按环境配置 week/startTime/endTime 做时间窗口过滤
- 支持规则级 webhook 和数据源级 webhook
- WebClient 带独立连接池（`grafana-connection-pool`）
- 分片拉取：每次最多 500 条，超过则继续拉取下个时间窗口
- 失败重试：最多 2 次，间隔 1s
- 日志推送 + 持久化 lastTs 到 `log_collect_time_info` 表（含采集数量统计）

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
- `executeAble()` 校验：执行窗口、频率、次数限制
- 失败文件重试（`ExecuteFailedRetry` 调用 `executeRetry()`）
- 失败计数每 8 次告警
- 无上限次数文件（`SQLConfig.unLimitCheckFiles`）失败不计次数

### 6.4 SqlConfigService.java（动态连接池管理）

**特点**:
- 从数据库读取数据源配置，调用 `refreshConfig()` 重建
- 使用 `DefaultListableBeanFactory.registerSingleton()` 动态注册 Bean
- 先销毁旧 Bean 再注册新 Bean，避免冲突
- `HikariDataSource` 配置：
  - `initializationFailTimeout=-1` 忽略启动连接失败
  - `maximumPoolSize=1` 每个数据源单连接
  - `maxLifetime=120s` 短生命周期适应 VPN 环境
  - `keepaliveTime=30s` 保持连接活性

### 6.5 SendWechatService.java（企业微信推送）

**推送逻辑**:
```java
sendMsg(MsgForm):
  // SQL 异常 → 优先用 MsgForm.webhook（来自数据源）
  // 无 webhook → 使用 baseConfig.wechatWebhook（全局）

sendMsgAndStore(msg, msgType, webhook, log):
  // 20:00~08:00 → sendStatus=false, 明天补推
  // 其他时间 → POST 企业微信 → sendStatus=true

pushMsg(): // 09:30 定时
  // 逐条发送，间隔 5s
  // 每条独立保存，互不影响
  // 失败只跳过当前条（无 @Transactional）
```

### 6.6 OperationLogAspect.java（操作日志切面）

**特点**:
- `@Around` 环绕增强，拦截所有标注 `@OperationLog` 的方法
- 自动获取 IP（支持 X-Forwarded-For 等代理头）
- 自动对比修改前后字段，记录变更详情
- 新增字段时新值为 null 跳过（避免未传字段产生干扰变更记录）
- 异步持久化到 `operation_log` 表（不阻塞业务）

### 6.7 ExecuteJDBCContext.java（执行上下文）

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
  "date": "2026-06-05"
}
```

#### GET /api/stats/dashboard
获取仪表盘统计数据

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
获取各环境推送分类统计（SQL/日志）

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
获取各环境累计和当日日志采集统计

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

> 新增 `filename` 参数，操作日志会记录调试的 SQL 文件名。

### 7.5 数据源管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sql/datasources` | SQL 数据源列表 |
| POST | `/api/sql/datasources` | 创建 SQL 数据源 |
| PUT | `/api/sql/datasources/{id}` | 更新 SQL 数据源 |
| DELETE | `/api/sql/datasources/{id}` | 删除 SQL 数据源 |
| POST | `/api/sql/refresh` | 刷新 SQL 配置 |
| GET | `/api/grafana/datasources` | Grafana 数据源列表 |
| POST | `/api/grafana/datasources` | 创建 Grafana 数据源 |
| PUT | `/api/grafana/datasources/{id}` | 更新 Grafana 数据源 |
| DELETE | `/api/grafana/datasources/{id}` | 删除 Grafana 数据源 |
| GET | `/api/grafana/datasources/{id}/rules` | 获取数据源的规则列表 |
| POST | `/api/grafana/rules` | 创建 Grafana 规则 |
| PUT | `/api/grafana/rules/{id}` | 更新 Grafana 规则 |
| DELETE | `/api/grafana/rules/{id}` | 删除 Grafana 规则 |
| POST | `/api/grafana/refresh` | 刷新 Grafana 配置 |

> 所有增删改操作自动触发 `refreshConfig()` 重建内存缓存。

### 7.6 操作日志 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logs` | 分页查询（支持 type/module/日期范围） |
| GET | `/api/logs/modules` | 获取模块列表 |

### 7.7 其他 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/environments` | 获取有采集统计的环境列表 |
| GET | `/api/datasources` | 获取 SQL 数据源名称列表 |
| GET | `/api/push-records` | 推送记录分页查询 |
| GET | `/api/stats/dashboard` | 首页仪表盘数据 |

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

#### msg_send_log（消息推送日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| content | TEXT | 推送内容 |
| send_webhook | TEXT | 推送目标 webhook |
| msg_type | TEXT | 消息类型（text/markdown） |
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
| 外键 | CASCADE | data_source_id → grafana_data_source(id) |

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

#### log_collect_time_info（日志采集时间/统计）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 主键 |
| environment_name | TEXT | 环境名称 |
| rule_name | TEXT | 规则名称 |
| last_ts | BIGINT | 最后采集时间戳（毫秒） |
| last_time | TIMESTAMP | 最后采集时间 |
| total_collect_count | BIGINT | 累计采集日志条数 |
| daily_collect_count | BIGINT | 当日采集日志条数 |
| collect_date | DATE | 采集日期 |
| create_time | TIMESTAMP | 创建时间 |

---

## 9. 配置文件详解

> 参考 `application.yml` 实际内容，涵盖所有配置项。注意 *当前多数配置已迁移到数据库管理*，YML 仅用于首次启动的默认值和全局 webhook。

### 9.1 核心配置结构

| 配置路径 | 作用 | 现管理方式 |
|----------|------|-----------|
| `server.port` | 应用端口 4000 | YML |
| `spring.datasource` | SQLite 本地数据库 | YML |
| `mybatis-plus` | MyBatis 配置 | YML |
| `management` | Actuator/Prometheus 监控端点 | YML |
| `watcher.notify-webhook` | 全局 webhook（SQL/日志） | YML |
| `watcher.sql.*` | SQL 数据源配置 | **已迁移到 DB** |
| `watcher.log.grafana.list` | Grafana 数据源配置 | **已迁移到 DB** |
| `watcher.log.local` | 本地日志配置 | YML（已禁用） |

### 9.2 配置优先级

1. 数据库动态配置（SQL/Grafana 数据源、规则）
2. 命令行参数 `-Dxxx=yyy`
3. `application.yml` 全局默认值（webhook 兜底）

---

## 10. 定时任务系统

### 10.1 线程池配置

负责 `@Async` 注解的线程池在 `ScheduleConfig` 中定义：

| 线程池名 | corePoolSize | maxPoolSize | queueCapacity | 用途 |
|----------|-------------|-------------|---------------|------|
| `executorSQL` | 2 | 4 | 10 | SQL 执行调度 |
| `retrySQL` | 2 | 4 | 10 | SQL 重试 |
| `grafanaLog` | 5 | 10 | 50 | Grafana 日志采集 |
| `operationLog` | 2 | 4 | 100 | 操作日志持久化 |

### 10.2 任务列表

| 任务名 | 触发机制 | 功能说明 | 备注 |
|--------|----------|----------|------|
| GrafanaLogServiceImp.supplement() | `@Scheduled(fixedRate=30s)` | Grafana Loki 日志采集 | 独立线程池+连接池 |
| ExecutorScheduler.executor() | `@Scheduled(fixedRate=240s)` | SQL 执行检测 | 每 4 分钟 |
| ExecuteFailedRetry.retry() | `@Scheduled(fixedRate=300s)` | 重试失败的 SQL | 每 5 分钟 |
| GrafanaDataSourceHealthChecker.checkHealth() | `@Scheduled(fixedRate=300s)` | Grafana 健康检查 | WebClient 重试 |
| SqlDataSourceHealthChecker.checkHealth() | `@Scheduled(fixedRate=300s)` | SQL 健康检查 | JdbcTemplate |
| SendWechatService.pushMsg() | `0 30 9 * * ?` | 免打扰补推 | 09:30 |
| ExecutorLogClear.clear() | `0 0 0 * * ?` | 清理 14 天前日志 | 每天凌晨 |

---

## 11. 开发历史（Git Log）

### 11.1 最近提交记录

```
2026-06-05 a1485d5 fix: 补推消息时移除@Transactional避免部分失败回滚全部状态
2026-06-05 db51ba4 fix: 操作日志字段对比时跳过空值字段，避免未传字段产生错误变更记录
2026-06-04 68345a7 feat: SQL数据源推送支持数据源级别webhook，未配置时走全局兜底
2026-06-04 bd16ff9 fix: 首次启动时CommandLineRunner导入数据后未刷新数据源配置
2026-06-04 d31f9ca fix: 操作日志详情文本框启用自动换行
2026-06-04 25126e5 feat: 操作日志日期选择改为flatpickr日期范围组件
2026-06-01 c9f3863 feat: SQL调试操作日志记录SQL文件名
2026-06-01 3c604f9 feat: 推送记录页面添加分页功能
2026-06-01 eecb8fc fix: 修改操作日志接口，手动查询总数替代依赖分页插件统计
2026-06-01 1297c3a fix: 修复MyBatis Plus分页插件，SQLite应使用DbType.SQLITE
2026-06-01 7d142dd fix: 添加MyBatis Plus分页插件配置
2026-06-01 5940ffb feat: 优化操作日志页面 - 合并日期筛选、移除查询按钮
2026-06-01 28f1d98 feat: 为所有统计卡片添加闪烁动画效果
2026-06-01 adff17f fix: 修复在线SQL数据源图标问题
2026-06-01 764cb67 fix: IP访问首页统计优化，当天同一IP只记录一条
2026-06-01 23bc556 fix: 心跳检查也添加连接池配置
2026-05-31 118f282 fix: 添加缺失的import语句
2026-05-31 64a9f75 feat: 添加按环境统计累计日志采集数量功能
2026-05-31 f0cce29 fix: 添加重试机制解决连接被服务器关闭的问题
2026-05-31 f4de15f fix: 添加连接池配置，修复连接被服务器关闭后无法恢复的问题
2026-05-31 2ac7da4 fix: 修复Grafana日志采集服务的URL拼接问题
2026-05-31 398a0ef fix: 修复SQL数据源刷新时Bean注册冲突问题
2026-05-31 bba2003 fix: 修正操作日志字段映射，匹配实体类实际字段名
2026-05-31 ad17deb feat: 实现操作日志记录修改前后值对比
2026-05-31 5753d0f fix: 优化操作日志记录，优先记录复杂对象
2026-05-31 ~ 操作日志系统：实现操作日志注解、切面和查询页面
2026-05-30 ~ 首页改版、数据源状态监控、健康检查解耦、SQL数据源动态管理
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
- **定时频率调整**: Grafana 30s、SQL 240s、重试 300s
- **连接池/重试**: 全链路连接池 + 重试机制

### 11.3 关键技术决策

| 决策 | 说明 |
|------|------|
| SQLite vs MySQL | 选择 SQLite 作为本地存储，简化部署（无需单独数据库） |
| MyBatis-Plus | 简化 CRUD 操作 + 分页插件 |
| WebFlux Reactor 版本锁定 | 统一 3.4.34/1.0.39 避免 webflux 冲突 |
| 健康检查 WebClient | 改用真实 API 调用而非 ping，独立连接池 |
| 健康检查频率 5 分钟 | 平衡时效性和网络开销，失败重试 2 次 |
| @Async 线程池 | 隔离日志采集/SQL执行/重试/操作日志各自线程 |
| 配置管理 | 首次启动 YML 导入 DB，后续通过 Web 页面动态管理 |
| 数据源级 webhook | 不同环境异常可推送到不同企业微信群 |
| 补推无事务 | 每条消息独立保存，一条失败不影响其他消息 |

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
4. **免打扰时段推送** — 20:00-08:00 的消息在 09:30 补推，**旧版 @Transactional 可能导致状态回滚**
5. **离线数据源处理** — 离线数据源不执行任务，避免无效操作
6. **首次启动初始化顺序** — `SqlDataInitializer`/`GrafanaDataInitializer` 导入数据后必须调用 `refreshConfig()`
7. **操作日志空值** — 更新接口只传部分字段时，**未传字段新值为 null，已被切面跳过不记录**
8. **SQL 数据源 webhook** — 非 SQL 异常走 `baseConfig.wechatWebhook`，SQL 异常优先走数据源级 webhook
9. **switch fall-through** — `sendMsgAndStore()` 中的 switch 已改为 if/else，避免 text 穿透到 markdown

### 13.3 性能优化

1. **健康检查频率**: 5 分钟 + 2 次重试
2. **连接池**: Grafana 独立连接池，SQL 动态 HikariCP
3. **SQL 执行限流**: 每个数据源最大 1 连接
4. **日志级别**: 生产环境 INFO，`com.szh.monitor.service.impl` DEBUG
5. **线程池隔离**: 日志采集/SQL执行/重试/操作日志各自独立

---

## 14. 常见问题与解决

### Q1: 编译报错 "package javax.servlet does not exist"
**解决**: Spring Boot 2.7.x 使用 `javax.servlet`，勿引入 `jakarta.servlet`

### Q2: WebFlux Reactor 版本冲突
**解决**: pom.xml 排除冲突依赖，统一 reactor-core 3.4.34

### Q3: 数据源离线但实际可连
**可能原因**:
1. 首次启动时 CommandLineRunner 导入数据后未刷新配置 → 已修复
2. 健康检查时 Bean 不存在 → 检查 `SqlConfigService.refreshConfig()` 是否被调用

### Q4: HikariCP 连接超时
**解决**:
```yaml
maximum-pool-size: 1
connection-timeout: 60000
max-lifetime: 120000
keepalive-time: 30000
```

### Q5: 企业微信推送失败
**排查**:
1. 检查 webhook URL 是否正确（全局在 YML，数据源级在数据库）
2. Webhook 优先级：规则级 → 数据源级 → 全局

### Q6: 补推消息重复推送
**历史原因**: 旧版 `@Transactional` 导致部分失败回滚全部状态。**已修复**：移除事务，每条独立保存。

### Q7: 操作日志出现"在线状态 修改前 是 修改后 -"
**历史原因**: 字段对比时没有跳过 null 新值。**已修复**：新值为 null 跳过不记录。

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

**文档版本**: v2.0  
**最后更新**: 2026-06-05  
**更新内容**: 同步所有后端/前端变更至 v2026-06-05，新增操作日志系统、数据源级 webhook、动态配置管理、健康检查重写、定时频率调整、连接池优化、初始化顺序修复等
