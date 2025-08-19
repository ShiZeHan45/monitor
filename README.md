****本项目目前实现了能读取某个文件夹下面的SQL检测脚本，执行后如果存在数据则通过企业微信机器人推送消息，达到外挂检测数据并实时报警的效果，以下是配置说明
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
app:
  sql-dir: classPath:monitor  # SQL文件存放目录
  sql-absolute-dir: 你的SQL文件夹绝对路径  # SQL文件绝对路径 优先级最高，有配置就会读取，不重启的情况下增加SQL检测文件
#  sql-dir: D:\\SZH\\monitor
  wechat-webhook: 你的企业微信机器人回调入口
#  schedule-cron: "0 0/2 * * * ?"  # 每5分钟执行一次
  schedule-cron: "0 0/30 * * * ?"  # 每60分钟执行一次
```


