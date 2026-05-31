# 监控系统项目分析文档

> **文档目的**: 帮助 AI 模型或其他开发人员快速理解项目架构、代码逻辑和开发流程  
> **项目类型**: Spring Boot 后端监控系统  
> **最后更新**: 2026-05-31

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
│  ┌──────────────┐    ┌──────────────┐    │           │ │
│  │ 本地日志文件  │ →  │  上下文截取   │ →  │           │ │
│  └──────────────┘    └──────────────┘    └───────────┘ │
│  ┌──────────────┐    ┌──────────────┐                  │
│  │ SQL 执行器   │ →  │  多数据源     │ →  ┌───────────┐ │
│  │ (定时/手动)  │    │  PostgreSQL   │ →  │           │ │
│  └──────────────┘    │  MySQL       │    │ 企业微信   │ │
│                       └──────────────┘    │  推送     │ │
│                                          └───────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 1.3 支持的监控环境

- **Grafana 环境**: 郑州生产、东莞生产、南昌生产、测试环境、开发环境
- **SQL 数据源**: 郑州生产、南昌生产、莲上-南澳自来水生产

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
spring-boot-starter-webflux  # 响应式 Web
spring-boot-starter-jdbc     # JDBC 支持
spring-boot-starter-actuator # 应用监控
spring-boot-starter-quartz   # 定时任务
mybatis-plus-boot-starter    # ORM 框架
micrometer-registry-prometheus # Prometheus 指标
```

### 2.2 数据存储

| 数据库 | 驱动版本 | 用途 |
|--------|----------|------|
| SQLite | 3.45.1.0 | 本地数据存储（规则、日志、配置） |
| PostgreSQL | 42.7.7 | 远程 SQL 检测数据源 |
| MySQL | 8.0.30 | 远程 SQL 检测数据源 |

### 2.3 响应式编程

```xml
<!-- Reactor 版本统一管理，避免冲突 -->
reactor-core: 3.4.34
reactor-netty-core: 1.0.39
reactor-netty-http: 1.0.39
```

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
├── deploy.exp                        # Expect 自动部署脚本
│                                     # - 用途: 自动上传 JAR 到服务器
│
├── app.log                          # 应用运行时日志
│
├── .gitignore                       # Git 忽略配置
│
├── README.md                        # 项目说明文档
│
├── 迭代研发交付规范.md              # 研发流程规范
│
└── src/
    ├── main/
    │   ├── java/com/szh/monitor/
    │   │   │
    │   │   ├── ProxyApplication.java    # ⭐ 启动类
    │   │   │                            # - @SpringBootApplication
    │   │   │                            # - @EnableScheduling
    │   │   │                            # - 启动时初始化日志监听
    │   │   │
    │   │   ├── config/                  # 配置类
    │   │   │   ├── BaseConfig.java
    │   │   │   ├── GrafanaConfig.java   # Grafana Loki 配置
    │   │   │   ├── LocalLogConfig.java  # 本地日志配置
    │   │   │   ├── MonitorRules.java    # 监控规则配置
    │   │   │   ├── MultiDataSourceConfig.java # 多数据源配置
    │   │   │   ├── SQLConfig.java       # SQL 执行配置
    │   │   │   ├── SQLiteDataSourceConfig.java # SQLite 配置
    │   │   │   ├── WebConfig.java       # Web 配置
    │   │   │   └── ... 其他配置
    │   │   │
    │   │   ├── context/                 # 上下文工具
    │   │   │   ├── ExecuteJDBCContext.java  # JDBC 执行上下文
    │   │   │   └── SpringContextUtil.java   # Spring Bean 获取工具
    │   │   │
    │   │   ├── controller/              # REST API 控制器
    │   │   │   ├── MonitorController.java     # 核心监控 API
    │   │   │   ├── GrafanaController.java      # Grafana 配置 API
    │   │   │   ├── SqlController.java          # SQL 管理 API
    │   │   │   └── OperationLogController.java # 操作日志 API
    │   │   │
    │   │   ├── entity/                  # 数据库实体
    │   │   │   ├── GrafanaDataSource.java     # Grafana 数据源
    │   │   │   ├── GrafanaMonitorRule.java     # Grafana 监控规则
    │   │   │   ├── LogCollectTimeInfo.java     # 日志采集时间
    │   │   │   ├── MsgSendLog.java             # 消息推送日志
    │   │   │   ├── OperationLog.java           # 操作日志
    │   │   │   ├── SqlDataSource.java          # SQL 数据源
    │   │   │   ├── SqlExecuteLog.java          # SQL 执行日志
    │   │   │   └── SqlExecuteRule.java          # SQL 执行规则
    │   │   │
    │   │   ├── mapper/                  # MyBatis Mapper
    │   │   │   └── [实体名]Mapper.java   # 对应每个实体
    │   │   │
    │   │   ├── service/                 # 服务接口
    │   │   │   ├── WatchService.java         # 日志监听接口
    │   │   │   ├── ExecutorService.java      # SQL 执行接口
    │   │   │   ├── SendService.java          # 消息发送接口
    │   │   │   └── ... 其他服务接口
    │   │   │
    │   │   ├── service/impl/            # 服务实现 ⭐
    │   │   │   ├── DispatchLogService.java      # 日志分发服务
    │   │   │   ├── GrafanaLogServiceImp.java    # Grafana 日志监听
    │   │   │   ├── LocalLogFileServiceImp.java  # 本地日志监听
    │   │   │   ├── SqlExecutorService.java      # SQL 执行服务
    │   │   │   ├── SendWechatService.java       # 企业微信推送
    │   │   │   └── SendDispatchService.java    # 消息分发
    │   │   │
    │   │   ├── scheduled/               # 定时任务
    │   │   │   ├── ExecutorScheduler.java     # SQL 执行调度
    │   │   │   ├── ExecuteFailedRetry.java    # 失败重试
    │   │   │   ├── ExecutorLogClear.java     # 日志清理
    │   │   │   ├── GrafanaDataSourceHealthChecker.java # Grafana 健康检查
    │   │   │   └── SqlDataSourceHealthChecker.java    # SQL 数据源健康检查
    │   │   │
    │   │   ├── form/                    # 表单对象
    │   │   │   ├── MsgForm.java
    │   │   │   └── WechatMessage.java
    │   │   │
    │   │   ├── vo/                      # 视图对象
    │   │   │   └── MsgVO.java
    │   │   │
    │   │   ├── enums/                   # 枚举类
    │   │   │   └── MsgType.java
    │   │   │
    │   │   └── exception/               # 异常类
    │   │       └── SQLExecutorFailException.java
    │   │
    │   └── resources/
    │       ├── application.yml         # ⭐ 主配置文件
    │       └── static/                  # 前端静态资源
    │           ├── index.html           # 首页/仪表盘
    │           ├── grafana-config.html  # Grafana 配置页
    │           ├── sql-config.html     # SQL 数据源配置页
    │           ├── sql-rules.html       # SQL 规则管理页
    │           ├── sql-upload.html      # SQL 文件上传页
    │           └── push-records.html    # 推送记录页
    │
    └── test/java/...                   # 测试代码
```

### 3.2 核心文件说明

| 文件路径 | 重要性 | 说明 |
|----------|--------|------|
| `ProxyApplication.java` | ⭐⭐⭐ | 启动类，初始化日志监听 |
| `application.yml` | ⭐⭐⭐ | 配置文件，包含所有配置项 |
| `pom.xml` | ⭐⭐ | Maven 配置，依赖管理 |
| `MonitorController.java` | ⭐⭐ | 核心 API，包含统计、规则管理 |
| `GrafanaLogServiceImp.java` | ⭐⭐ | Grafana 日志监听实现 |
| `SqlExecutorService.java` | ⭐⭐ | SQL 执行服务 |
| `SendWechatService.java` | ⭐⭐ | 企业微信推送 |

---

## 4. 核心功能模块

### 4.1 日志监听模块

**职责**: 监听 Grafana Loki 和本地日志文件，检测错误并推送告警

**核心类**: 
- `GrafanaLogServiceImp.java` - Grafana Loki 远程日志
- `LocalLogFileServiceImp.java` - 本地日志文件

**功能流程**:
```
1. 启动时初始化定时扫描任务
2. 连接 Grafana Loki API
3. 执行 LogQL 查询获取日志
4. 关键词匹配 + 排除关键词过滤
5. 截取上下文日志行
6. 去重检查（时间窗口）
7. 时间段检查（可选）
8. 调用企业微信推送
```

**关键配置**: `application.yml` → `watcher.log`

### 4.2 SQL 检测模块

**职责**: 定时执行 SQL 脚本，检测业务异常并推送告警

**核心类**:
- `SqlExecutorService.java` - SQL 执行逻辑
- `ExecutorScheduler.java` - 定时调度
- `ExecuteFailedRetry.java` - 失败重试

**功能流程**:
```
1. 扫描 SQL 目录（/soft/monitor）
2. 检查执行规则（每日次数限制）
3. 遍历 SQL 文件执行
4. 查询结果判断（有数据=异常）
5. 记录执行日志
6. 失败重试机制（5分钟间隔）
7. 企业微信推送
```

**关键配置**: `application.yml` → `watcher.sql`

### 4.3 企业微信推送模块

**职责**: 统一消息推送，支持免打扰时段

**核心类**:
- `SendWechatService.java` - 微信 API 调用
- `SendDispatchService.java` - 消息分发

**功能特性**:
- 免打扰时间: 20:00 - 08:00
- 补推时间: 早上 9:30
- 推送记录: 持久化到 SQLite

### 4.4 Web 管理界面

**前端技术**: 原生 HTML + JavaScript（无框架）

**页面列表**:
1. `index.html` - 仪表盘，展示统计数据
2. `grafana-config.html` - Grafana 数据源配置
3. `sql-config.html` - SQL 数据源配置（**动态管理**）
4. `sql-rules.html` - SQL 执行规则管理
5. `sql-upload.html` - SQL 文件上传下载
6. `push-records.html` - 推送历史查询

---

## 5. 架构设计说明

### 5.1 分层架构

```
┌────────────────────────────────────────┐
│           Web 层 (Controllers)          │
│  MonitorController, GrafanaController │
└─────────────────┬──────────────────────┘
                  │ REST API
┌─────────────────▼──────────────────────┐
│           Service 层 (Services)        │
│  GrafanaLogService, SqlExecutorService │
└─────────────────┬──────────────────────┘
                  │ 业务逻辑
┌─────────────────▼──────────────────────┐
│           Mapper 层 (MyBatis-Plus)     │
│  MsgSendLogMapper, SqlExecuteLogMapper│
└─────────────────┬──────────────────────┘
                  │ SQL 操作
┌─────────────────▼──────────────────────┐
│           数据层 (Data Sources)         │
│  SQLite (本地) + PostgreSQL/MySQL (远程)│
└────────────────────────────────────────┘
```

### 5.2 定时任务调度

```
┌────────────────────────────────────────┐
│           Quartz 调度器                 │
├────────────────────────────────────────┤
│  ┌──────────────────────────────────┐  │
│  │ ExecutorScheduler               │  │
│  │ 触发: 每 29 分钟执行一次          │  │
│  │ 任务: 扫描 SQL 目录并执行检测     │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ ExecuteFailedRetry               │  │
│  │ 触发: 每 5 分钟执行一次           │  │
│  │ 任务: 重试执行失败的 SQL         │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ GrafanaDataSourceHealthChecker   │  │
│  │ 触发: 每 5 分钟执行一次           │  │
│  │ 任务: 检查 Grafana 连接状态      │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ SqlDataSourceHealthChecker       │  │
│  │ 触发: 每 5 分钟执行一次           │  │
│  │ 任务: 检查 SQL 数据源连接状态    │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ ExecutorLogClear                 │  │
│  │ 触发: 每天凌晨执行               │  │
│  │ 任务: 清理旧的执行日志           │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

### 5.3 数据流向

```
Grafana Loki ──→ GrafanaLogService ──→ SendDispatchService ──→ SendWechatService
    │                  │                       │                        │
    │ 解析日志         │ 检查规则              │ 消息格式化            │ HTTP POST
    │ 关键词匹配       │ 去重检查              │ 免打扰检查            │ 企业微信API
    ▼                  ▼                       ▼                        ▼
 日志查询         异常判断               延迟发送处理             推送成功
```

---

## 6. 核心类详解

### 6.1 ProxyApplication.java（启动类）

```java
@SpringBootApplication
@EnableScheduling  // 启用定时任务
public class ProxyApplication implements CommandLineRunner {
    
    @Autowired
    private DispatchLogService dispatchLogService;
    
    @Autowired
    private LogCollectTimeInfoService logCollectTimeInfoService;
    
    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        // 启动时初始化日志监听
        dispatchLogService.startWatching();
        // 初始化日志采集时间映射
        logCollectTimeInfoService.initLastTSMAP();
    }
}
```

**作用**:
1. Spring Boot 应用入口
2. 启用定时任务调度
3. 启动时初始化日志监听服务

### 6.2 MonitorController.java（核心控制器）

**职责**: 提供监控系统的所有 REST API

**主要 API**:
- `/api/stats/*` - 统计数据接口
- `/api/sql-rules/*` - SQL 规则 CRUD
- `/api/sql-files/*` - SQL 文件管理
- `/api/datasources` - 数据源信息
- `/api/sql-debug/execute` - SQL 调试执行

**关键方法**:
```java
@GetMapping("/stats/today")
// 返回: 今日推送统计、SQL 执行统计

@GetMapping("/stats/dashboard")
// 返回: 仪表盘统计数据
// - todayPushCount: 今日推送数
// - onlineDataSourceCount: 在线数据源数
// - activeRuleCount: 活跃规则数
// - last24hExceptionCount: 24小时异常数

@PostMapping("/sql-rules")
// 创建 SQL 执行规则

@PostMapping("/sql-files/upload")
// 上传 SQL 文件到 /soft/monitor 目录
```

### 6.3 GrafanaLogServiceImp.java（日志监听服务）

**职责**: 监听 Grafana Loki 日志

**核心方法**:
```java
@Service
public class GrafanaLogServiceImp implements WatchService {
    
    // 定时扫描 Grafana 日志
    @Scheduled(fixedRate = 60000) // 每分钟扫描
    public void watchGrafanaLogs() {
        // 1. 遍历配置的 Grafana 环境
        // 2. 连接 Loki API
        // 3. 执行 LogQL 查询
        // 4. 匹配关键词
        // 5. 去重检查
        // 6. 推送告警
    }
}
```

**Loki API 调用**:
```java
// 查询日志
POST {url}/loki/api/v1/query_range
{
    "query": "{service=\"boss-bcs\"} |= \" ERROR \"",
    "limit": 100,
    "start": <时间戳>,
    "end": <时间戳>
}
```

### 6.4 SqlExecutorService.java（SQL 执行服务）

**职责**: 执行 SQL 脚本并判断结果

**核心逻辑**:
```java
public void executeSqlFiles(String environmentName, String jdbcTemplateName) {
    // 1. 获取 SQL 目录文件列表
    // 2. 过滤要执行的文件（根据规则）
    // 3. 遍历执行每个 SQL 文件
    for (File sqlFile : sqlFiles) {
        // 4. 检查执行次数限制
        // 5. 读取 SQL 内容
        // 6. 执行查询
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        // 7. 判断结果（有数据=异常）
        if (!results.isEmpty()) {
            // 8. 发送告警
            sendDispatchService.sendMsg(...);
        }
        
        // 9. 记录执行日志
        sqlExecuteLogService.logExecute(...);
    }
}
```

### 6.5 SendWechatService.java（微信推送服务）

**职责**: 调用企业微信机器人 API 推送消息

**推送逻辑**:
```java
public void sendWechatMsg(String webhook, String content) {
    // 1. 检查免打扰时间（20:00 - 08:00）
    LocalTime now = LocalTime.now();
    if (now.isAfter(QUIET_START) && now.isBefore(QUIET_END)) {
        // 2. 加入延迟队列，早上 9:30 补推
        delayedMessageQueue.add(content);
        return;
    }
    
    // 3. 发送 HTTP POST 请求
    String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + webhook;
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    Map<String, Object> body = new HashMap<>();
    body.put("msgtype", "text");
    body.put("text", Map.of("content", content));
    
    restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
}
```

---

## 7. API 接口文档

### 7.1 统计相关 API

#### GET /api/stats/today
获取今日统计数据

**响应**:
```json
{
  "pushTotal": 15,
  "pushStats": {
    "sql": 10,
    "log": 5
  },
  "sqlStats": {
    "郑州生产": {
      "totalCount": 5,
      "failedCount": 1
    }
  },
  "date": "2026-05-31"
}
```

#### GET /api/stats/dashboard
获取仪表盘统计数据

**响应**:
```json
{
  "todayPushCount": 15,
  "onlineDataSourceCount": 3,
  "totalDataSourceCount": 5,
  "activeRuleCount": 20,
  "last24hExceptionCount": 45
}
```

#### GET /api/stats/datasource-status
获取所有数据源的在线状态

**响应**:
```json
[
  {
    "name": "郑州生产",
    "type": "Grafana",
    "isOnline": true,
    "enabled": true,
    "lastCheckTime": "2026-05-31 10:00:00"
  },
  {
    "name": "郑州生产",
    "type": "SQL",
    "isOnline": true,
    "enabled": true,
    "lastCheckTime": "2026-05-31 10:00:00"
  }
]
```

### 7.2 SQL 规则管理 API

#### GET /api/sql-rules
获取所有 SQL 规则

#### POST /api/sql-rules
创建新规则

**请求体**:
```json
{
  "environmentName": "郑州生产",
  "sqlFileName": "check_order.sql",
  "executeInterval": 1,
  "enabled": true
}
```

#### PUT /api/sql-rules/{id}
更新规则

#### DELETE /api/sql-rules/{id}
删除规则

### 7.3 SQL 文件管理 API

#### GET /api/sql-files
获取 SQL 文件列表

#### POST /api/sql-files/upload
上传 SQL 文件

**请求**: `multipart/form-data`, 字段名: `file`

#### GET /api/sql-files/{filename}/content
获取文件内容

#### PUT /api/sql-files/{filename}/content
更新文件内容

**请求体**:
```json
{
  "content": "SELECT * FROM orders WHERE status = 'failed'"
}
```

### 7.4 SQL 调试 API

#### POST /api/sql-debug/execute
在线执行 SQL（调试用）

**请求体**:
```json
{
  "environment": "郑州生产",
  "sql": "SELECT COUNT(*) FROM orders"
}
```

**响应**:
```json
{
  "success": true,
  "columns": ["count"],
  "rows": [{"count": 100}],
  "rowCount": 1,
  "message": "查询成功，返回 1 条记录"
}
```

---

## 8. 数据库设计

### 8.1 SQLite 表结构

项目使用 SQLite 作为本地数据库，存储在 `/soft/sqlite/monitor.db`

#### msg_send_log（消息推送日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| content | TEXT | 推送内容 |
| environment_name | VARCHAR | 环境名称 |
| msg_type | VARCHAR | 消息类型 |
| create_time | DATETIME | 创建时间 |

#### sql_execute_log（SQL 执行日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| environment_name | VARCHAR | 环境名称 |
| sql_file_name | VARCHAR | SQL 文件名 |
| execute_date | INTEGER | 执行日期 |
| count | INTEGER | 执行次数 |
| failed_count | INTEGER | 失败次数 |
| create_time | DATETIME | 创建时间 |

#### sql_execute_rule（SQL 执行规则）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| environment_name | VARCHAR | 环境名称 |
| sql_file_name | VARCHAR | SQL 文件名 |
| execute_interval | INTEGER | 执行间隔（天） |
| enabled | BOOLEAN | 是否启用 |
| create_time | DATETIME | 创建时间 |

#### sql_data_source（SQL 数据源）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| environment_name | VARCHAR | 环境名称 |
| jdbc_url | VARCHAR | JDBC 连接地址 |
| username | VARCHAR | 用户名 |
| password | VARCHAR | 密码（加密存储） |
| driver_class_name | VARCHAR | 驱动类名 |
| is_online | INTEGER | 在线状态 |
| last_check_time | DATETIME | 最后检查时间 |
| enabled | INTEGER | 是否启用 |

#### grafana_data_source（Grafana 数据源）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| environment_name | VARCHAR | 环境名称 |
| url | VARCHAR | Grafana URL |
| datasource_id | VARCHAR | Loki 数据源 ID |
| username | VARCHAR | 用户名 |
| password | VARCHAR | 密码 |
| is_online | INTEGER | 在线状态 |
| last_check_time | DATETIME | 最后检查时间 |
| enabled | INTEGER | 是否启用 |

#### operation_log（操作日志）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| operation_type | VARCHAR | 操作类型 |
| target_id | VARCHAR | 目标 ID |
| description | TEXT | 操作描述 |
| ip_address | VARCHAR | IP 地址 |
| create_time | DATETIME | 创建时间 |

---

## 9. 配置文件详解

### 9.1 application.yml 完整配置

```yaml
# ============================================
# 服务端口配置
# ============================================
server:
  port: 4000

# ============================================
# SQLite 本地数据库
# ============================================
spring:
  datasource:
    jdbcUrl: jdbc:sqlite:/soft/sqlite/monitor.db
    driverClassName: org.sqlite.JDBC
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 30000

# ============================================
# Actuator 监控端点
# ============================================
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
  server:
    port: 18081

# ============================================
# 日志配置
# ============================================
logging:
  level:
    root: INFO
    com.szh.monitor.service.impl: debug
  file:
    name: app.log

# ============================================
# 监控配置（核心）
# ============================================
watcher:
  # 企业微信 Webhook
  notify-webhook:
    wechat-webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
    log-wechat-webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
  
  # SQL 检测配置
  sql:
    sql-dir: classPath:monitor
    sql-absolute-dir: /soft/monitor
    
    datasource:
      list:
        primary:
          environment-name: 郑州生产
          enabled: true
          jdbc-url: jdbc:postgresql://10.65.4.25:1560/prod_saas_thinkwater
          username: ax_read
          password: Read@2025
          driver-class-name: org.postgresql.Driver
        
        secondary:
          environment-name: 南昌生产
          enabled: true
          jdbc-url: jdbc:postgresql://10.65.4.44:15000/prod_saas_thinkwater
          # ...
        
        tertiary:
          environment-name: 莲上-南澳自来水生产
          enabled: true
          jdbc-url: jdbc:mysql://10.0.0.168:8399/waterhub_bill
          # ...
  
  # 本地日志监听
  log:
    local:
      enabled: false
      error-log-path: /data/wwwlogs/boss-bcs/error/boss-bcs.error.log
      keywords: ERROR,Exception,Failed
      context-lines: 20
      dedup-window-minutes: 10
      name: "5.0开发环境"
    
    # Grafana 日志监听
    grafana:
      list:
        - environment-name: "郑州生产"
          url: "http://10.65.4.25:3000"
          datasource-id: "2"
          username: "dev"
          password: "Anso@dev2025"
          monitors:
            - name: "BOSS-BCS"
              query-expr: '{service="boss-bcs"}'
              keywords: [" ERROR "]
              exclusion-keywords: ["获取短信模板id配置失败", ...]
              context-lines: 10
              enabled: true
```

### 9.2 配置优先级

1. 命令行参数 `-Dxxx=yyy`
2. `application.yml` 中的 `spring.config.location`
3. `application.yml` 默认配置

---

## 10. 定时任务系统

### 10.1 Quartz 配置

项目使用 Spring Boot 内置的 Quartz 进行定时任务调度。

### 10.2 任务列表

| 任务名 | 触发表达式 | 功能说明 |
|--------|------------|----------|
| ExecutorScheduler | `0 0/29 * * * ?` | 每 29 分钟执行一次 SQL 检测 |
| ExecuteFailedRetry | `0 0/5 * * * ?` | 每 5 分钟重试失败的 SQL |
| ExecutorLogClear | `0 0 0 * * ?` | 每天凌晨清理旧日志 |
| GrafanaDataSourceHealthChecker | `0 0/5 * * * ?` | 每 5 分钟检查 Grafana 连接 |
| SqlDataSourceHealthChecker | `0 0/5 * * * ?` | 每 5 分钟检查 SQL 数据源连接 |

### 10.3 任务实现示例

```java
@Scheduled(cron = "0 0/29 * * * ?")
public void scheduledExecute() {
    log.info("开始执行定时 SQL 检测任务");
    
    // 获取所有启用的 SQL 数据源
    List<SqlDataSource> dataSources = sqlDataSourceService.getEnabledDataSources();
    
    for (SqlDataSource ds : dataSources) {
        try {
            // 执行 SQL 检测
            sqlExecutorService.executeSqlFiles(
                ds.getEnvironmentName(),
                ds.getJdbcTemplateName(),
                null
            );
        } catch (Exception e) {
            log.error("SQL 检测异常: {}", ds.getEnvironmentName(), e);
        }
    }
}
```

---

## 11. 开发历史（Git Log）

### 11.1 最近提交记录

```
2026-05-31 3e82733 操作日志系统：在MonitorController添加访问和操作记录
2026-05-31 e252439 实现操作日志系统：记录访问和增删改操作
2026-05-31 c2ae066 修复导入语句，使用javax.servlet替代jakarta.servlet
2026-05-31 49d44a5 实现操作日志系统：记录访问和增删改操作
2026-05-31 5f0a760 调整首页关键指标：添加今日SQL异常和日志异常推送数
2026-05-31 3b5e507 调整首页顺序：消息推送统计移到数据源在线状态前面
2026-05-31 bc28bd3 修改数据源列表排序：先按类型排序，每种类型在线排前面
2026-05-31 e8b7dd8 添加首页统计：关键指标卡片和数据源在线状态
2026-05-31 e1f5bc1 fix: 首页统计只显示有执行次数的SQL数据源
2026-05-31 ab3d7cf feature: 离线数据源不再执行任务
2026-05-31 24edcd8 debug: 健康检查改用/api/org并添加调试日志
2026-05-31 5c6414e perf: 健康检查间隔从1分钟改为5分钟减少网络开销
2026-05-31 009751f fix: 改回健康检查用HTTP和JDBC真实连接检查
2026-05-31 4eec09e chore: 去掉健康检查的日常日志，只保留异常日志
2026-05-31 4cb4995 refactor: 简化健康检查为ping IP方式
2026-05-30 b97dfe1 refactor: 将Grafana和SQL数据源健康检查解耦为独立定时器服务
2026-05-30 21ba3b5 统一所有页面的导航菜单，确保SQL数据源配置链接在所有页面都存在
2026-05-30 24fb507 feat: 在首页添加SQL数据源配置导航链接
2026-05-30 3491d1e fix: 修复 application.yml 中 HikariCP 配置属性名错误
2026-05-30 2984e3d fix: 修复 webflux reactor 版本冲突问题
```

### 11.2 功能演进历程

#### 第一阶段：基础功能（早期提交）
- Grafana 日志监听
- SQL 执行检测
- 企业微信推送

#### 第二阶段：配置优化（2026-05-29 ~ 2026-05-30）
- **SQL 数据源动态管理**: 从 YML 配置改为数据库管理
- **健康检查机制**: Grafana 和 SQL 数据源健康检查
- **WebFlux Reactor 版本冲突**: 修复依赖版本冲突

#### 第三阶段：前端增强（2026-05-30 ~ 2026-05-31）
- **首页改版**: 添加关键指标卡片
- **数据源状态监控**: 显示在线/离线状态
- **导航统一**: 所有页面添加统一导航菜单

#### 第四阶段：操作日志（2026-05-31）
- **操作日志系统**: 记录所有访问和增删改操作
- **推送分类统计**: 区分 SQL 异常和日志异常

### 11.3 关键技术决策

| 决策 | 说明 |
|------|------|
| SQLite vs MySQL | 选择 SQLite 作为本地存储，简化部署 |
| MyBatis-Plus | 简化 CRUD 操作，提高开发效率 |
| WebFlux Reactor 版本 | 统一使用 3.4.34 / 1.0.39 避免冲突 |
| 健康检查频率 | 从 1 分钟改为 5 分钟，减少网络开销 |
| 离线数据源处理 | 离线数据源不再执行任务，避免无效操作 |

---

## 12. 部署流程

### 12.1 环境要求

| 环境 | 要求 |
|------|------|
| Java | 1.8+ |
| Maven | 3.6+ |
| 服务器 | Linux (CentOS/Ubuntu) |
| 磁盘 | 至少 1GB 可用空间 |

### 12.2 部署步骤

#### 步骤 1：本地打包
```bash
cd z:\monitor
mvn clean package
# 生成: target/actuator.jar
```

#### 步骤 2：上传到服务器
```bash
# 使用 scp 上传
scp target/actuator.jar root@192.168.199.85:/soft/actuator/

# 或使用部署脚本（需要 Expect）
expect deploy.exp
```

#### 步骤 3：服务器配置
```bash
# 创建目录
mkdir -p /soft/actuator
mkdir -p /soft/sqlite
mkdir -p /soft/monitor

# 上传 JAR 文件后
cd /soft/actuator
```

#### 步骤 4：启动应用
```bash
# 后台启动
nohup java -jar actuator.jar > app.log 2>&1 &

# 查看日志
tail -f app.log
```

#### 步骤 5：验证部署
```bash
# 检查进程
ps -ef | grep actuator.jar

# 健康检查
curl http://localhost:18081/actuator/health

# Prometheus 指标
curl http://localhost:18081/actuator/prometheus

# 访问首页
curl http://localhost:4000
```

### 12.3 常用运维命令

```bash
# 查看应用状态
ps -ef | grep actuator.jar

# 重启应用
pkill -f actuator.jar
cd /soft/actuator
nohup java -jar actuator.jar > app.log 2>&1 &

# 查看日志
tail -f app.log
tail -n 100 app.log

# 查看错误日志
grep ERROR app.log

# 查看端口占用
netstat -tlnp | grep -E '4000|18081'

# 清理日志文件
> app.log  # 清空日志
```

---

## 13. 开发注意事项

### 13.1 代码规范

1. **命名规范**
   - 类名：UpperCamelCase（例：`SqlExecutorService`）
   - 方法名：lowerCamelCase（例：`executeSqlFiles`）
   - 常量：UPPER_SNAKE_CASE（例：`MAX_RETRY_COUNT`）

2. **Git 提交规范**
   ```
   feat: 新功能
   fix: 缺陷修复
   refactor: 重构
   perf: 性能优化
   chore: 构建/工具相关
   docs: 文档更新
   ```

3. **异常处理**
   ```java
   // ✅ 推荐
   try {
       doSomething();
   } catch (SpecificException e) {
       logger.error("业务描述：{}", e.getMessage(), e);
       throw new BusinessException("友好错误信息");
   }
   
   // ❌ 避免
   try {
       doSomething();
   } catch (Exception e) {
       e.printStackTrace();
   }
   ```

### 13.2 常见陷阱

1. **WebFlux Reactor 版本冲突**
   - 必须在 pom.xml 中排除默认版本，统一使用 3.4.34 / 1.0.39

2. **SQLite 数据库路径**
   - 必须在服务器上创建 `/soft/sqlite` 目录
   - 确保应用有读写权限

3. **HikariCP 配置属性名**
   - 必须使用正确的属性名（如 `maximum-pool-size` 而非 `maxPoolSize`）

4. **免打扰时段推送**
   - 20:00 - 08:00 的消息会在 9:30 补推
   - 使用 `delayedMessageQueue` 管理延迟消息

5. **离线数据源处理**
   - 离线数据源不再执行任务（新增功能）
   - 避免对不可用的数据源进行无效操作

### 13.3 性能优化

1. **健康检查频率**: 从 1 分钟改为 5 分钟
2. **数据库连接池**: HikariCP 配置合理的连接数
3. **SQL 执行限流**: 每个数据源最大 1 个连接
4. **日志级别**: 生产环境使用 INFO，避免大量 DEBUG 日志

---

## 14. 常见问题与解决

### Q1: 编译报错 "package javax.servlet does not exist"

**原因**: Spring Boot 2.7.x 使用 `javax.servlet`，而非 `jakarta.servlet`

**解决**:
```java
// ✅ 正确
import javax.servlet.http.HttpServletRequest;

// ❌ 错误
import jakarta.servlet.http.HttpServletRequest;
```

### Q2: WebFlux Reactor 版本冲突

**症状**:
```
Could not resolve org.springframework.boot:spring-boot-starter-webflux::
Conflicting versions detected between dependencies
```

**解决**: 在 pom.xml 中排除冲突的依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### Q3: SQLite 数据库初始化失败

**症状**: `SQLite数据库无法创建表`

**解决**:
1. 创建目录: `mkdir -p /soft/sqlite`
2. 授权: `chmod 777 /soft/sqlite`
3. 重启应用

### Q4: HikariCP 连接超时

**症状**: `Connection is not available, request timed out`

**解决**: 检查 application.yml 配置
```yaml
hikari:
  maximum-pool-size: 1       # 单数据源设为 1
  connection-timeout: 60000  # 增加到 60 秒
```

### Q5: 企业微信推送失败

**可能原因**:
1. Webhook 地址错误
2. 网络不通
3. 消息格式错误

**排查步骤**:
```bash
# 测试 Webhook 是否可用
curl -X POST "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"msgtype": "text", "text": {"content": "test"}}'
```

---

## 15. 项目规范

### 15.1 参考文档

- [迭代研发交付规范.md](迭代研发交付规范.md) - 详细的研发流程规范
- [README.md](README.md) - 项目快速入门

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
| 功能异常 | 监控指标异常 | 停止当前进程，启动上一版本 JAR |
| 性能下降 | 响应时间超标 | 回滚至上一版本 |
| 数据异常 | 数据一致性问题 | 回滚数据库 + 应用 |

### 15.4 监控告警

- **Prometheus**: `http://192.168.199.85:18081/actuator/prometheus`
- **健康检查**: `http://192.168.199.85:18081/actuator/health`
- **应用日志**: `/soft/actuator/app.log`

---

## 📞 联系方式

如有问题，请查看：
1. 应用日志: `tail -f /soft/actuator/app.log`
2. Git 提交记录: `git log --oneline -20`
3. 操作日志: 访问 `/api/operation-logs`

---

**文档版本**: v1.1  
**最后更新**: 2026-05-31  
**更新内容**: 结合 Git Log 完善开发历史，添加架构说明和常见问题解答
