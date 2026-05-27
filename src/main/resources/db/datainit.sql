INSERT OR IGNORE INTO sql_data_source (environment_name, jdbc_url, username, password, driver_class_name, enabled, maximum_pool_size, minimum_idle, max_lifetime, idle_timeout, connection_timeout, keepalive_time, connection_test_query, validation_timeout, create_time, update_time) VALUES
('郑州生产', 'jdbc:postgresql://10.65.4.25:1560/prod_saas_thinkwater?TimeZone=Asia/Shanghai&tcpKeepAlive=true', 'ax_read', 'Read@2025', 'org.postgresql.Driver', 1, 1, 0, 120000, 30000, 60000, 30000, 'SELECT 1', 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('南昌生产', 'jdbc:postgresql://10.65.4.44:15000/prod_saas_thinkwater?TimeZone=Asia/Shanghai&tcpKeepAlive=true', 'ax_read', 'Anso@2026', 'org.postgresql.Driver', 1, 1, 0, 120000, 30000, 60000, 30000, 'SELECT 1', 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('莲上-南澳自来水生产', 'jdbc:mysql://10.0.0.168:8399/waterhub_bill?useUnicode=true&characterEncoding=utf8&characterSetResults=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai', 'dx_reader', 'Dx_reader=123', 'com.mysql.cj.jdbc.Driver', 1, 1, 0, 120000, 30000, 60000, 30000, 'SELECT 1', 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO remote_log_source (environment_name, url, datasource_id, username, password, week, start_time, end_time, monitors, enabled, create_time, update_time) VALUES
('郑州生产', 'http://10.65.4.25:3000', '2', 'dev', 'Anso@dev2025', '1,2,3,4,5,6,7', NULL, NULL, '[{"name":"BOSS-BCS","queryExpr":"{service=\"boss-bcs\"}","keywords":[" ERROR "],"exclusionKeywords":["获取短信模板id配置失败","无法获取客户code","device序列化转换字典异常","操作人员名称转换失败","正累计不能为空","委外机构已完成对账，不允许重新对账","PrePayServiceImpl.java:428","022504198","052300318","172.16.219.154:80","使用默认配置 【ACTIVITY,CONTRACT,RECHARGE_DISCOUNT】","当前存在未回填的临时结账任务","getLoginAppUser(SocketAuthListener.java:76)","您的订单已结束，请前往订单列表查看","157 Authorization error","自定义金额","大于账户可用余额"],"contextLines":10,"enabled":true}]', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('东莞生产', 'http://10.0.9.126:3000', '2', 'shizehan', 'Tax@123456', '1,2,3,4,5,6,7', NULL, NULL, '[{"name":"BOSS-BCS","queryExpr":"{service=\"boss-bcs\"}","keywords":[" ERROR "],"exclusionKeywords":["获取短信模板id配置失败","无法获取客户code","device序列化转换字典异常","操作人员名称转换失败","正累计不能为空","委外机构已完成对账，不允许重新对账","PrePayServiceImpl.java:428","022504198","052300318","172.16.219.154:80","使用默认配置 【ACTIVITY,CONTRACT,RECHARGE_DISCOUNT】","当前存在未回填的临时结账任务","getLoginAppUser(SocketAuthListener.java:76)","您的订单已结束，请前往订单列表查看","157 Authorization error","自定义金额","大于账户可用余额"],"contextLines":10,"enabled":true}]', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('南昌生产', 'http://10.65.4.44:3000', '1', 'dev', 'Anso@dev2025', '1,2,3,4,5,6,7', '09:00', '21:20', '[{"name":"BOSS-BCS","queryExpr":"{project=\"nc-ddw-5.0\", service=\"boss-bcs\"}","keywords":[" ERROR "],"exclusionKeywords":["获取短信模板id配置失败","无法获取客户code","device序列化转换字典异常","操作人员名称转换失败","正累计不能为空","委外机构已完成对账，不允许重新对账","PrePayServiceImpl.java:428","022504198","052300318","172.16.219.154:80","使用默认配置 【ACTIVITY,CONTRACT,RECHARGE_DISCOUNT】","当前存在未回填的临时结账任务","getLoginAppUser(SocketAuthListener.java:76)","您的订单已结束，请前往订单列表查看","157 Authorization error","自定义金额","大于账户可用余额"],"contextLines":10,"enabled":true}]', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('测试环境', 'http://10.0.9.149:3000', '1', 'shizehan', 'Tax@123456', '1,2,3,4,5,6,7', NULL, NULL, '[{"name":"BOSS-BCS","queryExpr":"{service=\"boss-bcs\"}","keywords":[" ERROR "],"exclusionKeywords":["获取短信模板id配置失败","无法获取客户code","device序列化转换字典异常","操作人员名称转换失败"],"contextLines":10,"enabled":false}]', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('开发环境', 'http://192.168.199.85:3000', '1', 'admin', 'admin', '1,2,3,4,5,6,7', NULL, NULL, '[{"name":"BOSS-BCS","queryExpr":"{job=\"boss-bcs\"} |= \"[boss-bcs:192.168.199.85:32092]\"!= \"level=info\"","keywords":[" ERROR "],"exclusionKeywords":["获取短信模板id配置失败","无法获取客户code"],"contextLines":1,"enabled":false}]', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO log_collect_time_info (environment_name, rule_name, last_ts, last_time, create_time) VALUES
('郑州生产', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('南昌生产', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('莲上-南澳自来水生产', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('东莞生产', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('测试环境', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('开发环境', 'DEFAULT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);




-- 批量插入初始化数据
INSERT OR IGNORE INTO sql_execute_rule (
    environment_name,
    sql_file_name,
    execute_limit,
    execute_start_time,
    execute_end_time,
    execute_frequency
)
VALUES
    ('郑州生产', '开阀检查-应开未开.sql', 99, '15:00:00','20:00:00', 60),
    ('郑州生产', '下发关阀指令1天后阀门状态仍为开阀.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '当日应关阀未关阀检查.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '优惠到期账户0量需手动关阀limit5.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '当日计划下发关阀用水户.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '当日已下发关阀的用水户limit5.sql', 1, '12:00:00','20:00:00', 60),
    ('郑州生产', '存在关阀指令下发1天后仍继续用水的异常用水户.sql', 1, '11:00:00','20:00:00', 60),

    ('郑州生产', '开账系统与营收待出账水量平衡检查统计.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '日预结算水量与实时计算水量对比limit5.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '存在未对账处理的交易.sql', 1, '14:00:00','20:00:00', 60),
    ('郑州生产', '阶梯累计平衡检查limit5.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '未上报读数设备数量统计.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '日结算数据上报情况.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '应定期结账未结账.sql', 1, '10:00:00','20:00:00', 60),
    ('郑州生产', '不连续抄读指度检查limit1-25年7月1号以后的数据.sql', 1, '11:00:00','20:00:00', 60),
    ('郑州生产', '账户流水期初期末连续性检查limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '未入册但有在用水的用水户.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '统计测量参数不含OPEN_VALUE的设备.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '下单回调时间差超过2分钟的支付订单limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '账户与优惠绑定的水量和金额平衡检查limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '当天存在微信支付销账失败数据limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '5分钟前存在待消费的管线机取水订单.sql', 99, '10:00:00','20:00:00', 60),
    ('郑州生产', '账户优惠记录和计费政策调整余量平衡检查.sql', 99, '10:00:00','20:00:00', 60),


    ('南昌生产', '开账系统与营收待出账水量平衡检查统计.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '日预结算水量与实时计算水量对比limit5.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '存在未对账处理的交易.sql', 1, '14:00:00','20:00:00', 60),
    ('南昌生产', '阶梯累计平衡检查limit5.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '未上报读数设备数量统计.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '不连续抄读指度检查limit1-25年7月1号以后的数据.sql', 1, '11:00:00','20:00:00', 60),
    ('南昌生产', '日结算数据上报情况.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '应定期结账未结账.sql', 1, '10:00:00','20:00:00', 60),
    ('南昌生产', '账户流水期初期末连续性检查limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '未入册但有在用水的用水户.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '统计测量参数不含OPEN_VALUE的设备.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '下单回调时间差超过2分钟的支付订单limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '账户与优惠绑定的水量和金额平衡检查limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '当天存在微信支付销账失败数据limit5.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '5分钟前存在待消费的管线机取水订单.sql', 99, '10:00:00','20:00:00', 60),
    ('南昌生产', '账户优惠记录和计费政策调整余量平衡检查.sql', 99, '10:00:00','20:00:00', 60),

    ('莲上-南澳自来水生产', '自来水-开票总额与账单总额不相等统计.sql', 99, '09:00:00','20:00:00', 60),
    ('莲上-南澳自来水生产', '自来水-深澳欠费账单平衡检查.sql', 99, '09:00:00','20:00:00', 60),
    ('莲上-南澳自来水生产', '自来水-青澳欠费账单平衡检查.sql', 99, '09:00:00','20:00:00', 60),
    ('莲上-南澳自来水生产', '自来水-云澳欠费账单平衡检查.sql', 99, '09:00:00','20:00:00', 60),
    ('莲上-南澳自来水生产', '自来水-莲上欠费账单平衡检查.sql', 99, '09:00:00','20:00:00', 60),
    ('莲上-南澳自来水生产', '自来水-后宅欠费账单平衡检查.sql', 99, '09:00:00','20:00:00', 60);