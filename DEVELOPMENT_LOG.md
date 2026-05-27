# 监控系统项目开发记录

> 本文档记录所有任务指令及完成情况，便于后续追溯和开发参考。

---

## 📅 日期：2026-05-27

### 一、项目分析任务

**用户指令：**
> 分析这个项目代码，输出你对整个项目的理解，以便我接下来要分配任务给你

**完成内容：**

对 `z:\monitor` 项目进行了全面的代码分析，涵盖以下方面：

#### 1. 项目概览
- **项目名称**：监控系统（日志 + SQL + 推送）
- **技术栈**：
  - Spring Boot 2.7.18
  - WebFlux（响应式编程）
  - MyBatis-Plus（ORM）
  - HikariCP（多数据源连接池）
  - SQLite（本地存储）
  - PostgreSQL（远程SQL检查）

#### 2. 核心功能模块

| 模块 | 功能 | 关键组件 |
|------|------|----------|
| 日志监听 | 本地+远程日志监控 | `GrafanaLogServiceImp`, `LocalLogFileServiceImp` |
| SQL检查 | SQL脚本执行监控 | `SqlExecutorService`, `ExecutorScheduler` |
| 推送通知 | 企业微信告警推送 | `SendDispatchService`, `SendWechatService` |

#### 3. 关键文件说明

- `ProxyApplication.java` - Spring Boot启动类
- `ExecutorScheduler.java` - SQL检查定时调度（29分钟间隔）
- `ExecuteFailedRetry.java` - 失败SQL重试调度（5分钟间隔）
- `SendDispatchService.java` - 推送总调度
- `GrafanaLogServiceImp.java` - Grafana Loki日志监听核心实现
- `SqlExecutorService.java` - SQL执行服务
- `SendWechatService.java` - 企业微信机器人推送
- `MultiDataSourceConfig.java` - 多数据源配置
- `MonitorRules.java` - 监控规则配置

#### 4. 配置特点
- 支持无限环境配置
- 每个数据源/监控项可独立启用/禁用
- 支持按星期、时间段过滤监控
- 日志推送支持去重时间窗口

#### 5. 相关文件
- [PROJECT_ANALYSIS.md](file:///z:/monitor/PROJECT_ANALYSIS.md)
- [README.md](file:///z:/monitor/README.md)

---

### 二、前端操作页面开发任务

**用户指令：**
> 请为这个项目研发一个前端操作页面，功能有
> - 首页：每日各个环境的推送统计、各个环境的SQL执行情况统计
> - 子功能：能看到每日各个环境的每条推送记录、能看到SQL检查规则，并对SQL检查规则进行维护、能有入口可以上传新的SQL

**完成内容：**

#### 1. 后端API开发

**新建文件：** [MonitorController.java](file:///z:/monitor/src/main/java/com/szh/monitor/controller/MonitorController.java)

实现了以下RESTful API接口：

| API路径 | 方法 | 功能说明 |
|---------|------|----------|
| `/api/stats/today` | GET | 获取今日统计数据（推送总数、SQL异常、日志异常、其他分类统计） |
| `/api/stats/environment` | GET | 获取各环境SQL执行情况统计（执行次数、失败次数） |
| `/api/push-records` | GET | 获取推送记录列表（支持分页page/size、日期筛选date） |
| `/api/sql-rules` | GET | 获取所有SQL规则列表 |
| `/api/sql-rules` | POST | 新增SQL规则 |
| `/api/sql-rules/{id}` | GET | 获取指定规则详情 |
| `/api/sql-rules/{id}` | PUT | 更新指定规则 |
| `/api/sql-rules/{id}` | DELETE | 删除指定规则 |
| `/api/sql-files` | GET | 获取已上传的SQL文件列表 |
| `/api/sql-files/upload` | POST | 上传SQL文件到配置目录 |
| `/api/sql-files/{filename}` | DELETE | 删除指定的SQL文件 |

#### 2. Web配置类

**新建文件：** [WebConfig.java](file:///z:/monitor/src/main/java/com/szh/monitor/config/WebConfig.java)

- 配置CORS跨域支持，允许前端访问API
- 配置静态资源路径，映射到 `classpath:/static/`

#### 3. 前端页面开发

##### 3.1 首页 - 统计概览

**新建文件：** [index.html](file:///z:/monitor/src/main/resources/static/index.html)

**功能特性：**
- 今日推送统计卡片（4个指标）：
  - 今日推送总数
  - SQL异常推送数量
  - 日志异常推送数量
  - 其他推送数量
- 各环境SQL执行情况表格：
  - 环境名称
  - 执行次数
  - 失败次数
  - 成功率（百分比进度条）
  - 状态标识（正常/异常）
- 今日推送记录预览（最近10条）
- 时间显示当前日期

**技术实现：**
- Tailwind CSS 样式
- Font Awesome 图标
- Fetch API 调用后端接口
- 响应式设计

##### 3.2 推送记录页面

**新建文件：** [push-records.html](file:///z:/monitor/src/main/resources/static/push-records.html)

**功能特性：**
- 日期筛选查询功能
- 分页展示推送记录（每页10条）
- 记录列表展示：
  - ID
  - 消息类型（异常/通知）
  - 内容摘要
  - 创建时间
  - 发送状态（已发送/待发送）
  - 操作（查看详情）
- 推送详情弹窗：
  - 消息类型
  - 发送Webhook
  - 创建时间
  - 发送时间
  - 发送状态
  - 完整内容

##### 3.3 SQL规则管理页面

**新建文件：** [sql-rules.html](file:///z:/monitor/src/main/resources/static/sql-rules.html)

**功能特性：**
- 规则列表展示：
  - ID
  - 环境名称
  - SQL文件名
  - 执行限制次数
  - 执行时间范围
  - 执行频率（分钟）
  - 操作（编辑/删除）
- 添加规则功能（弹窗表单）：
  - 环境名称（必填）
  - SQL文件名（必填）
  - 执行限制次数
  - 执行开始时间
  - 执行结束时间
  - 执行频率
- 编辑规则功能
- 删除规则功能（带确认提示）

##### 3.4 SQL文件上传页面

**新建文件：** [sql-upload.html](file:///z:/monitor/src/main/resources/static/sql-upload.html)

**功能特性：**
- 文件上传区域：
  - 拖拽上传支持
  - 点击上传支持
  - 仅接受 .sql 文件
  - 上传进度显示
  - 上传结果反馈（成功/失败）
- 已上传文件列表：
  - 显示所有SQL文件名
  - 支持删除文件（带确认提示）
  - 实时刷新文件列表

#### 4. 页面导航结构

统一侧边栏导航，包含：
- 首页统计
- 推送记录
- SQL规则管理
- SQL文件上传

---

### 三、启动方式

1. 确保项目已配置好数据库连接（SQLite数据库）
2. 确保 `application.yml` 中已配置：
   - 企业微信机器人Webhook
   - PostgreSQL数据源连接信息
   - SQL文件目录配置
3. 运行 `ProxyApplication.java` 启动Spring Boot应用
4. 访问 http://localhost:4000/ 打开前端管理页面

---

### 四、文件清单

| 类型 | 文件路径 |
|------|----------|
| 后端控制器 | `src/main/java/com/szh/monitor/controller/MonitorController.java` |
| Web配置 | `src/main/java/com/szh/monitor/config/WebConfig.java` |
| 前端首页 | `src/main/resources/static/index.html` |
| 推送记录页 | `src/main/resources/static/push-records.html` |
| SQL规则管理页 | `src/main/resources/static/sql-rules.html` |
| SQL文件上传页 | `src/main/resources/static/sql-upload.html` |

---

### 五、后续维护说明

#### 数据库表结构

系统使用了以下SQLite表：

1. **msg_send_log** - 推送记录表
   - 字段：id, content, sendWebhook, msgType, createTime, sendDate, sendStatus

2. **sql_execute_log** - SQL执行日志表
   - 字段：id, environmentName, sqlFileName, executeDate, count, failedCount, failedCountResetTime

3. **sql_execute_rule** - SQL执行规则表
   - 字段：id, environmentName, sqlFileName, executeLimit, executeStartTime, executeEndTime, executeFrequency

#### API调用示例

```javascript
// 获取今日统计
fetch('/api/stats/today')

// 获取推送记录（带分页）
fetch('/api/push-records?page=1&size=10&date=2026-05-27')

// 获取SQL规则
fetch('/api/sql-rules')

// 上传SQL文件
const formData = new FormData();
formData.append('file', fileInput.files[0]);
fetch('/api/sql-files/upload', { method: 'POST', body: formData });
```

---

### 三、Git自动化提交配置

**用户指令：**
> 请你在每次修改项目完成后 自动组织提交信息，并推git

**完成内容：**
- 配置Git提交工作流程，每次开发任务完成后：
  1. 自动更新DEVELOPMENT_LOG.md文档
  2. 生成规范的提交信息
  3. 执行git add/commit/push操作
- 提交信息格式约定：
  - 功能新增：`feat: 简短描述`
  - 修复问题：`fix: 简短描述`
  - 文档更新：`docs: 简短描述`
  - 配置变更：`config: 简短描述`

---

### 四、WebConfig编译错误修复

**用户指令：**
> 你所创建的WebConfig，WebMvcConfigurer报错 [ERROR] /tmp/monitor-build/src/main/java/com/szh/monitor/config/WebConfig.java:[8,57] 程序包org.springframework.web.servlet.config.annotation不存在

**问题原因：**
- 项目使用WebFlux框架，而非Spring MVC
- `WebMvcConfigurer` 是Spring MVC的接口，在WebFlux环境中不可用

**修复方案：**
- 将 `WebMvcConfigurer` 改为 WebFlux 的 `WebFilter` 方式实现CORS配置
- 使用 `CorsUtils` 和响应式编程模型

**修改文件：** [WebConfig.java](file:///z:/monitor/src/main/java/com/szh/monitor/config/WebConfig.java)

---

### 五、Maven编译检查流程配置

**用户指令：**
> 你每次开发完后端程序，都要mvn 编译一下是否存在异常，没异常再推git

**完成内容：**
- 配置开发流程：每次后端代码修改完成后，先执行 `mvn compile` 检查编译是否通过
- 修复MonitorController中的类型转换错误：
  - `Map<String, Object>` 的 `merge` 方法不能直接使用 `Integer::sum` 方法引用
  - 改为显式类型转换的lambda表达式：`(a, b) -> (Integer) a + (Integer) b`

**修改文件：** [MonitorController.java](file:///z:/monitor/src/main/java/com/szh/monitor/controller/MonitorController.java)

---

### 六、SQL文件维护功能增强

**用户指令：**
> 现在需要你在SQL文件上传这个菜单将名字改为 SQL文件维护 然后已上传的文件支持编辑

**完成内容：**

#### 1. 菜单名称修改
- 将所有页面的「SQL文件上传」菜单名称改为「SQL文件维护」
- 图标从 `fa-upload` 改为 `fa-folder-open`
- 更新页面标题和页面标题

#### 2. 新增SQL文件编辑功能

**后端API新增：**

| API路径 | 方法 | 功能说明 |
|---------|------|----------|
| `/api/sql-files/{filename}/content` | GET | 获取SQL文件内容 |
| `/api/sql-files/{filename}/content` | PUT | 更新SQL文件内容 |

**前端功能：**
- 文件列表新增编辑按钮
- 点击编辑弹出模态框，显示文件名和内容
- 支持修改SQL文件内容并保存
- 保存成功后刷新文件列表

**修改文件：**
- [MonitorController.java](file:///z:/monitor/src/main/java/com/szh/monitor/controller/MonitorController.java) - 新增读取和更新文件内容的API
- [sql-upload.html](file:///z:/monitor/src/main/resources/static/sql-upload.html) - 新增编辑功能和模态框
- [index.html](file:///z:/monitor/src/main/resources/static/index.html) - 更新菜单名称
- [push-records.html](file:///z:/monitor/src/main/resources/static/push-records.html) - 更新菜单名称
- [sql-rules.html](file:///z:/monitor/src/main/resources/static/sql-rules.html) - 更新菜单名称

---

## 📝 记录更新日志

| 日期 | 操作类型 | 内容摘要 |
|------|----------|----------|
| 2026-05-27 | 项目分析 | 完成项目代码全面分析，输出架构设计和功能说明 |
| 2026-05-27 | 功能开发 | 完成前端操作页面全部功能（首页统计、推送记录、SQL规则管理、文件上传） |
| 2026-05-27 | 文档+配置 | 新增开发记录文档，配置Git自动化提交流程 |
| 2026-05-27 | 修复bug | 修复WebConfig编译错误，将WebMvcConfigurer改为WebFlux的WebFilter方式 |
| 2026-05-27 | 修复bug | 修复MonitorController中Map.merge方法的类型转换错误，编译成功 |
| 2026-05-27 | 功能增强 | 将SQL文件上传改为SQL文件维护，新增文件编辑功能 |

---

> 📌 **提示**：本文档将随后续开发任务持续更新。
