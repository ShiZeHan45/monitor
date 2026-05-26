# 监控系统（日志 + SQL + 推送）
支持无限环境配置 · 企业微信机器人实时推送 · WebFlux · MyBatis-Plus · 多数据源 HikariCP

---

## ✨ 功能特性

### 1. 日志监听（Log Watcher）
- **远程日志监听（Grafana Loki）**  
  根据关键词匹配日志，支持自动截取上下文行数并推送企业微信  
  配置参考：`watcher.log.grafana`
- **本地日志监听**  
  监控本地 log 文件，实时识别 `ERROR`、`Exception` 等关键词  
  配置参考：`watcher.log.local`

---

### 2. SQL 脚本检查（SQL Monitor）
- **自动读取 SQL 文件夹**
- **远程数据库执行 SQL 检查（PostgreSQL）**
- **执行失败自动重试（每 5 分钟）**
- **每日 SQL 执行次数限制（默认 2 次）**
- **可设置不限执行次数文件列表**

> 执行失败记录持久化在 SQLite  
> 重试成功后自动恢复正常频率

---

### 3. 企业微信机器人推送（Notify）
- SQL 执行异常推送
- 日志异常推送
- **免打扰时间：20:00 - 08:00**  
  期间的消息会在 **早上 9:30 补推**  
  （作者懒，没有做成可配置  自问自答：为什么是9点半补推？因为9点上班😂）

---

## 🛠 技术栈

- **Spring Boot 2.7.18**
- **WebFlux**
- **MyBatis-Plus**
- **HikariCP 多数据源**
- **SQLite（本地存储）**
- **PostgreSQL（远程 SQL 检查）**

---

# 📝 更新日志 (Changelog)

## **2025-12-11**
- 新增特征：SQL检查环境配置无上限，重新整理配置归属
- 升级底层springboot,重新整理pom依赖
- 

---

## **2025-12-02**
- 新增：Grafana 日志动态监听
- 新增：关键字识别 + 截取上下文推送
- 调整：YML 配置结构优化
- SQL 新增：每日执行次数限制
- SQL 新增：免次数限制文件配置

---

## **2025-11-26**
- Grafana 日志监听增强
- SQL 执行次数限制逻辑更新

---

## **2025-11-08**
- 新增本地日志拾取规范并推送企业微信
- 新增多数据源 `enabled=false` 控制是否加载

---


# ⚙️ 完整配置（YAML）

```
server:
  port: 4000
  
spring:
  datasource: # 程序固化数据使用的数据库
    jdbc-url: jdbc:sqlite:/soft/sqlite/monitor.db
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql
 
logging:
  level:
    root: INFO
  file:
    name: app.log

watcher:
  notify-webhook:
    wechat-webhook: 你的企业微信机器人入口
    log-wechat-webhook: 日志错误的企业微信入口

  sql: # sql检测 配置
    sql-dir: classPath:monitor  # SQL检测脚本文件读取的位置
    sql-absolute-dir: 绝对路径 # SQL检测脚本文件读取的位置
    check-limit: 2 # 每日检查上限
    un-limit-check-files: ["xxx.sql"] # 无上限的脚本
    schedule-cron: "0 0/29 * * * ?"  # SQL检查多久执行一次
    schedule-retry-cron: "0 0/5 * * * ?" # 执行失败的SQL文件多少重试一次
    datasource: #  配置连接参数
      list:
        primary:
          environment-name: 你的环境简称
          enabled: true # 是否开启，关闭则不检测此环境
          jdbc-url: jdbc:postgresql://yourip:yourport/yourDB?TimeZone=Asia/Shanghai&tcpKeepAlive=true
          username: yourusername
          password: yourpassword
          driver-class-name: org.postgresql.Driver
          hikari:
            maximum-pool-size: 1
            minimum-idle: 0
            max-lifetime: 120000
            idle-timeout: 30000
            connection-timeout: 60000
            keepalive-time: 30000
            connection-test-query: SELECT 1
            validation-timeout: 10000
        secondary:
          environment-name: 你的环境简称
          enabled: false
          jdbc-url: jdbc:postgresql://yourip:yourport/yourDB?TimeZone=Asia/Shanghai&tcpKeepAlive=true
          username: yourusername
          password: yourpassword
          driver-class-name: org.postgresql.Driver
          hikari:
            maximum-pool-size: 1
            minimum-idle: 0
            max-lifetime: 120000
            idle-timeout: 30000
            connection-timeout: 60000
            keepalive-time: 30000
            connection-test-query: SELECT 1
            validation-timeout: 10000

  log:
    local: # 本地日志监听
      enabled: false # 是否开启
      error:
        log:
          path: 你的日志文件路径 
      keywords: ERROR,Exception,Failed  # 捕获哪些关键词
      context-lines: 30 # 向下截取多少行日志
      dedup-window-minutes: 10 # 去重时间窗口 10分钟
      name: "xxx"  # 名称 用于推送信息的头部

    grafana:  # 远程日志监听
      list:
       - environment-name: "xxx" #第一个环境
         url: "http://ip:port"
         datasource-id: "2"  # 刚才查到的 ID  WINDOWS 可以使用curl -u "uesrname:password" http://ip:port/api/datasources 获取,响应数组,看到name为loki的对象,取对象里面的id
         username: "xx"   # 你的账号
         password: "xx" # 你的密码
         monitors: # 监听规则
             - name: "营收服务日志监控" # 监控简称
               query-expr: '{service="xxx"}' # LogQL 基础标签  要查哪个服务
               keywords: [ " ERROR "] ## 捕获哪些关键词
               exclusion-keywords: [ "xx"] ## 排除的关键词
               context-lines: 10 # 向下截取多少行日志
               enabled: true # 是否开启
       - environment-name: "xxx" #第二个环境
         url: "http://ip:port"
         datasource-id: "2"  # 刚才查到的 ID  WINDOWS 可以使用curl -u "uesrname:password" http://ip:port/api/datasources 获取,响应数组,看到name为loki的对象,取对象里面的id
         username: "xx"   # 你的账号
         password: "xxx" # 你的密码
         monitors: # 监听规则
             - name: "营收服务日志监控" # 监控简称
               query-expr: '{service="xxx"}' # LogQL 基础标签  要查哪个服务
               keywords: [ " ERROR "] ## 捕获哪些关键词
               exclusion-keywords: [ "xx"] ## 排除的关键词
               context-lines: 10 # 向下截取多少行日志
               enabled: true # 是否开启
```

---
