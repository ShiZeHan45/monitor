# Monitor 项目分析文档

## 1. 项目概述

**项目名称**: Monitor (监控代理服务)
**版本**: 1.0-SNAPSHOT
**技术栈**: Spring Boot 2.7.18 + Java 1.8

### 1.1 项目简介
Monitor 是一个企业级监控告警代理系统，集成了 Grafana Loki 日志监控、SQL 执行监控、本地日志文件监控等多种监控能力，并支持通过企业微信机器人进行告警通知。

### 1.2 核心技术选型
- **框架**: Spring Boot 2.7.18
- **数据库**: MySQL 8.0.30, PostgreSQL 42.7.7, SQLite 3.45.1.0
- **ORM**: MyBatis-Plus 3.5.2
- **定时任务**: Spring Scheduling + Quartz
- **HTTP客户端**: WebClient (WebFlux) + RestTemplate
- **监控**: Micrometer + Prometheus
- **连接池**: HikariCP

## 2. 系统架构

### 2.1 整体架构图
```
┌─────────────────────────────────────────────────────────────────┐
│                        Monitor 系统                              │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │   Web层      │  │   服务层    │  │   数据层    │            │
│  │ Controller  │  │   Service   │  │   Mapper    │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
│        │                │                │                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │GrafanaController│ │GrafanaLogService│ │SqlExecuteLogMapper│  │
│  │MonitorController│ │SqlExecutorService│ │MsgSendLogMapper│   │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
│                          │                                     │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                     定时任务调度层                        │  │
│  │ ExecutorScheduler │ ExecuteFailedRetry │ ExecutorLogClear│  │
│  └─────────────────────────────────────────────────────────┘  │
│                          │                                     │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    多数据源管理层                         │  │
│  │ MultiDataSourceConfig │ ExecuteJDBCContext │ HikariCP   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                          │                                     │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    外部服务集成层                         │  │
│  │    Grafana Loki    │   企业微信Webhook   │ 本地文件    │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 目录结构
```
src/main/java/com/szh/monitor/
├── ProxyApplication.java              # 应用启动类
├── config/                           # 配置类
│   ├── BaseConfig.java              # 基础配置（Webhook地址）
│   ├── GrafanaConfig.java           # Grafana配置
│   ├── GrafanaDataInitializer.java  # Grafana数据初始化
│   ├── LocalLogConfig.java          # 本地日志配置
│   ├── MonitorRules.java            # 监控规则配置
│   ├── MultiDataSourceConfig.java   # 多数据源配置
│   ├── MultipartConfig.java         # 文件上传配置
│   ├── ScheduleConfig.java          # 调度配置
│   ├── SQLConfig.java               # SQL配置
│   ├── SQLiteDataSourceConfig.java  # SQLite配置
│   ├── TransactionManagerConfig.java # 事务管理器配置
│   └── WebConfig.java               # Web配置
├── context/                         # 上下文工具
│   ├── ExecuteJDBCContext.java      # JDBC执行上下文
│   └── SpringContextUtil.java        # Spring上下文工具
├── controller/                      # 控制器层
│   ├── GrafanaController.java       # Grafana数据源/规则管理API
│   └── MonitorController.java       # 监控统计/规则/SQL文件管理API
├── enums/                           # 枚举类
│   └── MsgType.java                # 消息类型枚举
├── entity/                          # 实体类
│   ├── GrafanaDataSource.java       # Grafana数据源实体
│   ├── GrafanaMonitorRule.java      # Grafana监控规则实体
│   ├── LogCollectTimeInfo.java      # 日志收集时间信息实体
│   ├── MsgSendLog.java              # 消息发送日志实体
│   ├── SqlExecuteLog.java           # SQL执行日志实体
│   └── SqlExecuteRule.java          # SQL执行规则实体
├── exception/                       # 异常类
│   └── SQLExecutorFailException.java # SQL执行失败异常
├── form/                            # 表单/请求类
│   ├── MsgForm.java                # 消息表单
│   └── WechatMessage.java          # 企业微信消息格式
├── mapper/                         # 数据访问层
│   ├── GrafanaDataSourceMapper.java
│   ├── GrafanaMonitorRuleMapper.java
│   ├── LogCollectTimeInfoMapper.java
│   ├── MsgSendLogMapper.java
│   ├── SqlExecuteLogMapper.java
│   └── SqlExecuteRuleMapper.java
├── scheduled/                      # 定时任务
│   ├── ExecuteFailedRetry.java      # 失败重试任务
│   ├── ExecutorLogClear.java        # 日志清理任务
│   └── ExecutorScheduler.java       # 执行调度任务
├── service/                        # 服务接口
│   ├── ExecutorService.java         # 执行器接口
│   ├── GrafanaDataSourceService.java
│   ├── GrafanaMonitorRuleService.java
│   ├── LogCollectTimeInfoService.java
│   ├── MsgSendLogService.java
│   ├── SendService.java             # 消息发送接口
│   ├── SqlExecuteLogService.java
│   ├── SqlExecuteRuleService.java
│   ├── WatchService.java           # 文件监控接口
│   └── impl/                        # 服务实现
│       ├── DispatchLogService.java
│       ├── GrafanaDataSourceServiceImp.java
│       ├── GrafanaLogServiceImp.java
│       ├── GrafanaMonitorRuleServiceImp.java
│       ├── LocalLogFileServiceImp.java
│       ├── LogCollectTimeInfoServiceImp.java
│       ├── MsgSendLogServiceImp.java
│       ├── SendDispatchService.java
│       ├── SendWechatService.java
│       ├── SqlExecuteLogServiceImp.java
│       ├── SqlExecuteRuleServiceImp.java
│       └── SqlExecutorService.java
└── vo/                             # 值对象
    └── MsgVO.java
```

## 3. 功能模块分析

### 3.1 核心功能模块

#### 3.1.1 Grafana Loki 日志监控模块
**主要功能**: 从 Grafana Loki 查询日志，根据关键词匹配规则检测异常日志并推送告警。

**核心组件**:
- [GrafanaController.java](file:///Z:/monitor/src/main/java/com/szh/monitor/controller/GrafanaController.java) - 数据源和规则管理 API
- [GrafanaLogServiceImp.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/GrafanaLogServiceImp.java) - 核心日志监控实现

**数据流向**:
```
Grafana Loki → WebClient → GrafanaLogServiceImp.supplement() 
    → 关键词匹配 → SendDispatchService → Wechat Webhook → 企业微信
```

**关键特性**:
- 支持多数据源、多环境配置
- 按周几、时间段灵活调度
- 支持关键词匹配和排除关键词
- 自动记录上次查询时间戳，实现增量拉取
- 支持上下文行数捕获

#### 3.1.2 SQL 执行监控模块
**主要功能**: 定时执行 SQL 文件，根据执行结果触发告警。

**核心组件**:
- [SqlExecutorService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/SqlExecutorService.java) - SQL 执行器
- [ExecuteJDBCContext.java](file:///Z:/monitor/src/main/java/com/szh/monitor/context/ExecuteJDBCContext.java) - 执行上下文管理
- [MultiDataSourceConfig.java](file:///Z:/monitor/src/main/java/com/szh/monitor/config/MultiDataSourceConfig.java) - 多数据源配置

**数据流向**:
```
SQL文件 → SqlExecutorService.executeSqlFiles()
    → JdbcTemplate.queryForList() → ExecuteJDBCContext 计数
    → SendDispatchService.sendMsg() → Wechat Webhook → 企业微信
```

**关键特性**:
- 支持多环境（MySQL/PostgreSQL）动态切换
- 可配置的 SQL 执行时间窗口和频率
- 失败重试机制（最多重试8次）
- 执行次数限制控制
- 无上限检查文件配置

#### 3.1.3 本地日志文件监控模块
**主要功能**: 监控本地日志文件，捕获错误日志并推送告警。

**核心组件**:
- [LocalLogFileServiceImp.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/LocalLogFileServiceImp.java) - 文件监控实现
- [WatchService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/WatchService.java) - 监控服务接口
- [DispatchLogService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/DispatchLogService.java) - 监控调度

**关键特性**:
- 使用 Java NIO WatchService 实时监控
- 支持文件截断检测（logrotate）
- 去重机制（基于 SHA-1 哈希 + 时间窗口）
- 可配置关键词和上下文行数

#### 3.1.4 消息通知模块
**主要功能**: 统一的消息发送和日志记录。

**核心组件**:
- [SendService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/SendService.java) - 发送服务接口
- [SendWechatService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/SendWechatService.java) - 企业微信实现
- [SendDispatchService.java](file:///Z:/monitor/src/main/java/com/szh/monitor/service/impl/SendDispatchService.java) - 发送调度

**关键特性**:
- 支持 text 和 markdown 消息类型
- 夜间（20:00-08:00）消息缓存，定时补推
- 消息发送日志持久化
- 支持不同环境使用不同 Webhook

### 3.2 API 接口模块

#### 3.2.1 GrafanaController - Grafana 相关 API
| 接口路径 | 方法 | 功能 |
|---------|------|------|
| `/api/grafana/datasources` | GET | 获取所有数据源列表 |
| `/api/grafana/datasources/{id}` | GET | 获取指定数据源 |
| `/api/grafana/datasources` | POST | 创建数据源 |
| `/api/grafana/datasources/{id}` | PUT | 更新数据源 |
| `/api/grafana/datasources/{id}` | DELETE | 删除数据源 |
| `/api/grafana/datasources/{id}/rules` | GET | 获取数据源关联的规则 |
| `/api/grafana/rules/{id}` | GET | 获取指定规则 |
| `/api/grafana/rules` | POST | 创建规则 |
| `/api/grafana/rules/{id}` | PUT | 更新规则 |
| `/api/grafana/rules/{id}` | DELETE | 删除规则 |
| `/api/grafana/refresh` | POST | 刷新配置 |

#### 3.2.2 MonitorController - 监控统计 API
| 接口路径 | 方法 | 功能 |
|---------|------|------|
| `/api/stats/today` | GET | 获取今日统计 |
| `/api/stats/environment` | GET | 按环境获取统计 |
| `/api/stats/push-by-env` | GET | 按环境获取推送统计 |
| `/api/push-records` | GET | 分页查询推送记录 |
| `/api/sql-rules` | GET/POST/PUT/DELETE | SQL规则 CRUD |
| `/api/sql-rules/{id}` | GET | 获取指定规则 |
| `/api/sql-rules/check-unique` | GET | 检查规则唯一性 |
| `/api/sql-files` | GET | 获取SQL文件列表 |
| `/api/sql-files/upload` | POST | 上传SQL文件 |
| `/api/sql-files/{filename}` | DELETE | 删除SQL文件 |
| `/api/sql-files/{filename}/content` | GET | 获取文件内容 |
| `/api/sql-files/{filename}/content` | PUT | 更新文件内容 |
| `/api/datasources` | GET | 获取数据源列表 |
| `/api/sql-debug/execute` | POST | SQL调试执行 |
| `/api/environments` | GET | 获取环境列表 |

## 4. 数据模型

### 4.1 核心实体类

#### 4.1.1 GrafanaDataSource - Grafana数据源
```java
- id: Long                    // 主键
- url: String                // Grafana URL
- environmentName: String    // 环境名称
- datasourceId: String        // Loki 数据源 ID
- username: String           // 认证用户名
- password: String           // 认证密码
- webhook: String           // 企业微信 Webhook
- week: String               // 生效星期 [1,2,3,4,5,6,7]
- startTime: String          // 开始时间 HH:mm
- endTime: String            // 结束时间 HH:mm
- enabled: Integer          // 是否启用 0/1
- createTime: LocalDateTime // 创建时间
- updateTime: LocalDateTime // 更新时间
```

#### 4.1.2 GrafanaMonitorRule - Grafana监控规则
```java
- id: Long                  // 主键
- dataSourceId: Long        // 关联数据源ID
- name: String             // 规则名称（微服务名）
- queryExpr: String        // Loki 查询表达式
- keywords: String         // 匹配关键词 JSON
- exclusionKeywords: String // 排除关键词 JSON
- contextLines: Integer    // 上下文行数
- webhook: String         // Webhook（可覆盖数据源配置）
- enabled: Integer        // 是否启用
- createTime: LocalDateTime
- updateTime: LocalDateTime
```

#### 4.1.3 SqlExecuteRule - SQL执行规则
```java
- id: Long                 // 主键
- environmentName: String  // 环境名称
- sqlFileName: String     // SQL文件名
- executeLimit: Integer   // 每日执行次数限制
- executeStartTime: String // 开始时间 HH:mm:ss
- executeEndTime: String  // 结束时间 HH:mm:ss
- executeFrequency: Integer // 执行频率（分钟）
```

#### 4.1.4 SqlExecuteLog - SQL执行日志
```java
- id: Long
- environmentName: String  // 环境名称
- sqlFileName: String     // SQL文件名
- count: Integer          // 执行成功次数
- failedCount: Integer   // 失败次数
- executeDate: Integer   // 执行日期 yyyyMMdd
- failedCountResetTime: Integer // 失败计数重置时间
- createTime: LocalDateTime
- updateTime: LocalDateTime
```

#### 4.1.5 MsgSendLog - 消息发送日志
```java
- id: Long
- environmentName: String  // 环境名称
- msgType: String         // 消息类型 text/markdown
- content: String         // 消息内容
- sendWebhook: String    // 发送的 Webhook
- sendStatus: Boolean    // 发送状态
- sendDate: LocalDateTime // 发送时间
- createTime: LocalDateTime
```

### 4.2 数据库表结构

| 表名 | 说明 |
|-----|------|
| grafana_data_source | Grafana 数据源配置表 |
| grafana_monitor_rule | Grafana 监控规则表 |
| sql_execute_rule | SQL 执行规则表 |
| sql_execute_log | SQL 执行日志表 |
| msg_send_log | 消息发送日志表 |
| log_collect_time_info | 日志收集时间信息表 |

## 5. 定时任务设计

### 5.1 定时任务列表

| 任务类 | 方法 | 执行频率 | 功能 |
|-------|------|---------|------|
| ExecutorScheduler | executor() | 每4分钟 | 执行SQL检测任务 |
| ExecuteFailedRetry | retry() | 每5分钟 | 重试失败的SQL |
| ExecutorLogClear | clear() | 每日0:05 | 清理历史日志 |
| SendWechatService | pushMsg() | 每日9:30 | 补推夜间消息 |

### 5.2 Grafana 日志监控调度
- **初始延迟**: 10秒
- **执行间隔**: 30秒
- **执行方式**: 异步 (@Async)

### 5.3 执行流程图
```
┌──────────────────────────────────────────────┐
│           ExecutorScheduler (每4分钟)          │
└──────────────────┬───────────────────────────┘
                   │
          ┌────────▼────────┐
          │ 加载SQL执行规则  │
          └────────┬────────┘
                   │
          ┌────────▼────────┐
          │ 遍历执行器列表  │
          └────────┬────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼───┐    ┌─────▼─────┐   ┌────▼────┐
│执行SQL│    │ 执行日志监控 │   │ 执行... │
└───┬───┘    └─────┬─────┘   └────┬────┘
    │              │              │
    └──────────────┼──────────────┘
                   │
          ┌────────▼────────┐
          │  失败重试机制    │
          │ (ExecuteFailedRetry) │
          └─────────────────┘
```

## 6. 配置管理

### 6.1 配置文件结构
项目使用 YAML 配置，主要配置项：

#### 6.1.1 多数据源配置 (watcher.sql.datasource)
```yaml
watcher:
  sql:
    datasource:
      - environmentName: "生产环境"
        enabled: true
        jdbcUrl: "jdbc:mysql://..."
        username: "xxx"
        password: "xxx"
        driverClassName: "com.mysql.cj.jdbc.Driver"
        executeSql:
          - "check_order.sql"
        hikari:
          maximumPoolSize: 10
          minimumIdle: 5
```

#### 6.1.2 SQL 配置 (watcher.sql)
```yaml
watcher:
  sql:
    enable: true
    sqlDir: classpath:sql/
    sqlAbsoluteDir: /data/sql
    checkLimit: 100
    unLimitCheckFiles:
      - "unlimited.sql"
```

#### 6.1.3 Webhook 配置 (watcher.notify-webhook)
```yaml
watcher:
  notify-webhook:
    wechatWebhook: "https://qyapi.weixin.qq.com/..."
    logWechatWebhook: "https://qyapi.weixin.qq.com/..."
```

### 6.2 动态 Bean 注册
使用 `GenericApplicationContext` 实现运行时 Bean 注册：
```java
applicationContext.registerBean(beanName, JdbcTemplate.class, () -> jdbcTemplate);
```

## 7. 数据流向分析

### 7.1 Grafana 日志监控数据流
```
1. GrafanaLogServiceImp.supplement() 定时触发
2. 从 dataSourceInfoMap 获取配置
3. 使用 WebClient 向 Grafana Loki 发送请求
4. 解析 Loki 返回的日志数据
5. 根据关键词匹配日志
6. 构建告警消息
7. 通过 SendDispatchService 发送
8. 记录 MsgSendLog 到数据库
9. 更新 LogCollectTimeInfo 中的 lastTs
```

### 7.2 SQL 执行监控数据流
```
1. ExecutorScheduler.executor() 定时触发
2. 加载 SqlExecuteRule 规则
3. 遍历 SQL 文件夹获取文件
4. ExecuteJDBCContext 判断是否可执行
5. JdbcTemplate 执行 SQL
6. 记录 SqlExecuteLog
7. 若有结果，发送告警
8. 失败时记录失败次数
```

### 7.3 本地日志监控数据流
```
1. DispatchLogService 启动时创建线程
2. WatchService 监听文件变化
3. 读取新增内容
4. 关键词匹配
5. 去重检查（SHA-1）
6. 发送告警
```

## 8. 关键设计模式

### 8.1 策略模式
- `ExecutorService` 接口定义执行策略
- `SqlExecutorService` 实现 SQL 执行策略
- 便于扩展新的执行类型

### 8.2 模板方法模式
- `SendService` 定义发送模板
- 各实现类填充具体逻辑

### 8.3 观察者模式
- `WatchService` 定义文件监控
- `DispatchLogService` 管理多个监控实例

### 8.4 门面模式
- `SendDispatchService` 统一消息发送入口
- 隐藏内部多个 SendService 实现

## 9. 安全性分析

### 9.1 认证机制
- Grafana 数据源使用 Basic Auth
- Webhook 使用企业微信标准认证

### 9.2 SQL 注入防护
- 使用 JdbcTemplate 参数化查询
- SQL 文件从本地目录读取

### 9.3 文件上传安全
- 仅允许 .sql 文件
- 在配置目录内操作

## 10. 扩展建议

### 10.1 功能扩展
1. 支持更多消息通道（钉钉、飞书、邮件）
2. 增加告警升级机制
3. 支持告警抑制和静默期
4. 增加告警关联分析

### 10.2 性能优化
1. 使用连接池复用 WebClient
2. 增加查询结果缓存
3. 优化 SQL 执行日志查询

### 10.3 可用性提升
1. 增加健康检查端点
2. 支持配置热更新
3. 增加监控指标导出到 Prometheus

## 11. 总结

Monitor 项目是一个功能完整的企业级监控告警系统，具有以下特点：

**优点**:
- 模块化设计，职责清晰
- 支持多数据源动态切换
- 灵活的配置管理
- 完善的失败重试机制
- 统一的告警通知通道

**待改进**:
- 缺乏完整的单元测试
- 错误处理可更细化
- 可增加配置中心支持
- 日志记录可规范化
