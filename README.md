>本项目目前实现了读取并执行某个文件夹目录下的SQL检测脚本，执行后如果有数据响应，则将响应数据通过企业微信机器人推送出去，达到外挂检测SQL异常并实时报警的效果

> 从项目的规划上，后续可以实现各种协议的检测，只需要实现ExecutorService接口，即可拓展一种新的检测方式。推送同理，实现SendService即可

> 2025-11-08
> 
> 新增特性，根据配置的关键词拾取指定路径的日志文件，推送至企业微信
> 
> 新增多数据源可配置失效，enabled=false时，数据源将不会加载

> ❗数据库访问目前暂支持两个数据源的配置，一份SQL脚本可以检测两个不同的环境

# 怎么使用？
1. 配置两个数据源的datasource.config和datasource详见配置说明
2. 准备好你的检测SQL脚本
3. 将SQL脚本存放至项目的resources文件夹中的monitor文件夹，配置项为app.sql-dir或者用绝对路径app.sql-absolute-dir

# 关于重试机制
1. 在SQL执行失败时，改SQL文件会被标记
2. 被标记的SQL文件将会提高执行频率为5分钟执行一次，直至执行成功后停止重试


> 以下是配置说明
---------

```
server:
  port: 4000
  
datasource.config:
  primary:
    ip-port: yourip:yourport
    database-name: prod_saas_thinkwater
    username: yourusername
    password: yourpassword
    driver-class-name: org.postgresql.Driver
  secondary:
    ip-port: yourip:yourport
    database-name: prod_saas_thinkwater
    username: yourusername
    password: yourpassword
    driver-class-name: org.postgresql.Driver
  relation: xx环境-primary,xx环境-secondary

datasource.primary:
  jdbc-url: jdbc:postgresql://${datasource.config.primary.ip-port}/${datasource.config.primary.database-name}?TimeZone=Asia/Shanghai&tcpKeepAlive=true
  username: ${datasource.config.primary.username}
  password: ${datasource.config.primary.password}
  driver-class-name: ${datasource.config.primary.driver-class-name}
  hikari:
    # 连接池大小配置
    maximum-pool-size: 1
    minimum-idle: 1
    # 连接超时设置(毫秒)
    connection-timeout: 60000
    # 连接生命周期(毫秒)
    max-lifetime: 1800000
    # 空闲连接超时(毫秒)
    idle-timeout: 600000
    # 连接泄漏检测(毫秒)
    leak-detection-threshold: 60000
    # 连接验证
    connection-test-query: SELECT 1
    validation-timeout: 5000
    # 保持活动设置
    keepalive-time: 30000

datasource.secondary:
  enabled: false
  jdbc-url: jdbc:postgresql://${datasource.config.secondary.ip-port}/${datasource.config.secondary.database-name}?TimeZone=Asia/Shanghai&tcpKeepAlive=true
  username: ${datasource.config.secondary.username}
  password: ${datasource.config.secondary.password}
  driver-class-name: ${datasource.config.secondary.driver-class-name}
  hikari:
    # 连接池大小配置
    maximum-pool-size: 1
    minimum-idle: 1
    # 连接超时设置(毫秒)
    connection-timeout: 60000
    # 连接生命周期(毫秒)
    max-lifetime: 1800000
    # 空闲连接超时(毫秒)
    idle-timeout: 600000
    # 连接泄漏检测(毫秒)
    leak-detection-threshold: 60000
    # 连接验证
    connection-test-query: SELECT 1
    validation-timeout: 5000
    # 保持活动设置
    keepalive-time: 30000

logging:
  level:
   root: INFO
  file:
   name: app.log
watcher:
  error:
    log:
      path: 你的日志路径
  keywords: ERROR,Exception,Failed   ## 遇到哪些关键词就拾取
  context-lines: 30  ## 拾取多少行

app:
  sql-dir: classPath:monitor  # SQL文件存放目录
  sql-absolute-dir: 你的SQL文件夹绝对路径  # SQL文件绝对路径 优先级最高，有配置就会读取，不重启的情况下增加SQL检测文件
  wechat-webhook: 你的企业微信机器人回调入口
  log-wechat-webhook: 你的企业微信机器人回调入口 日志错误发送渠道
  schedule-cron: "0 0/29 * * * ?"  # 每29分钟执行一次
  schedule-retry-cron: "0 0/5 * * * ?"  # 执行失败重试定时器
```


