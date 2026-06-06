CREATE TABLE IF NOT EXISTS sql_execute_log (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               environment_name TEXT, -- 环境名称
                                               sql_file_name TEXT, -- SQL文件名称
                                               execute_date INTEGER, -- 执行日期
                                               failed_count INTEGER, -- 失败次数
                                               failed_count_reset_time INTEGER, -- 失败次数重置时间
                                               count INTEGER -- 执行次数
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_env_file_date ON sql_execute_log(environment_name, sql_file_name, execute_date);

CREATE TABLE IF NOT EXISTS msg_send_log(
                                           id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                           content TEXT, -- 推送内容
                                           send_webhook TEXT, -- 推送地址
                                           msg_type TEXT, -- 消息类型
                                           environment_name TEXT, -- 环境
                                           create_time TIMESTAMP , -- 内容产生时间
                                           send_date TIMESTAMP , -- 内容推送日期
                                           send_status INTEGER -- 已发送 1发送 0未发送
);

CREATE TABLE IF NOT EXISTS log_collect_time_info(
                                           id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                           environment_name TEXT, -- 环境名称
                                           rule_name TEXT, -- 规则名称
                                           create_time TIMESTAMP , -- 内容产生时间
                                           last_ts BIGINT , -- 最新的采集时间戳
                                           last_time TIMESTAMP -- 最新的采集时间
);

CREATE TABLE IF NOT EXISTS sql_execute_rule (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               environment_name TEXT, -- 环境名称
                                               sql_file_name TEXT, -- SQL文件名称
                                               execute_limit INTEGER, -- 执行上限次数
                                               execute_start_time TEXT, -- 从每天的什么时间开始执行 HH:MM:SS
                                               execute_end_time TEXT, -- 每天什么时候停止执行 HH:MM:SS
                                               execute_frequency INTEGER -- 执行频率 n分钟一次
);
-- 新增：环境+SQL文件 唯一索引（核心）
CREATE UNIQUE INDEX IF NOT EXISTS idx_env_sql_file
    ON sql_execute_rule(environment_name, sql_file_name);

CREATE TABLE IF NOT EXISTS grafana_data_source (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               url TEXT, -- Grafana API地址
                                               environment_name TEXT UNIQUE, -- 环境名称（唯一）
                                               datasource_id TEXT, -- 数据源ID
                                               username TEXT, -- 用户名
                                               password TEXT, -- 密码
                                               webhook TEXT, -- 默认推送地址
                                               week TEXT, -- 星期配置（JSON数组）
                                               start_time TEXT, -- 开始时间 HH:mm
                                               end_time TEXT, -- 结束时间 HH:mm
                                               enabled INTEGER DEFAULT 1, -- 是否启用 1启用 0禁用
                                               create_time TIMESTAMP, -- 创建时间
                                               update_time TIMESTAMP, -- 更新时间
                                               last_check_time TIMESTAMP, -- 最后检查时间
                                               is_online INTEGER DEFAULT 0 -- 是否在线 1在线 0离线
);

CREATE TABLE IF NOT EXISTS grafana_monitor_rule (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               data_source_id INTEGER, -- 数据源ID
                                               name TEXT, -- 规则名称
                                               query_expr TEXT, -- 查询表达式
                                               keywords TEXT, -- 关键词（JSON数组）
                                               exclusion_keywords TEXT, -- 排除关键词（JSON数组）
                                               context_lines INTEGER DEFAULT 5, -- 上下文行数
                                               webhook TEXT, -- 推送地址（为空时使用数据源默认地址）
                                               enabled INTEGER DEFAULT 1, -- 是否启用 1启用 0禁用
                                               create_time TIMESTAMP, -- 创建时间
                                               update_time TIMESTAMP, -- 更新时间
                                               FOREIGN KEY(data_source_id) REFERENCES grafana_data_source(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_rule_data_source_id
    ON grafana_monitor_rule(data_source_id);

CREATE TABLE IF NOT EXISTS sql_data_source (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               environment_name TEXT UNIQUE, -- 环境名称（唯一）
                                               jdbc_url TEXT, -- JDBC连接地址
                                               username TEXT, -- 用户名
                                               password TEXT, -- 密码
                                               driver_class_name TEXT, -- 驱动类名
                                               webhook TEXT, -- 默认推送地址
                                               week TEXT, -- 星期配置（JSON数组）
                                               start_time TEXT, -- 开始时间 HH:mm
                                               end_time TEXT, -- 结束时间 HH:mm
                                               enabled INTEGER DEFAULT 1, -- 是否启用 1启用 0禁用
                                               create_time TIMESTAMP, -- 创建时间
                                               update_time TIMESTAMP, -- 更新时间
                                               last_check_time TIMESTAMP, -- 最后检查时间
                                               is_online INTEGER DEFAULT 0 -- 是否在线 1在线 0离线
);

CREATE TABLE IF NOT EXISTS operation_log (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               ip TEXT, -- IP地址
                                               user_agent TEXT, -- User Agent
                                               operation_type TEXT, -- 操作类型
                                               module TEXT, -- 模块
                                               target_id INTEGER, -- 目标ID
                                               detail TEXT, -- 详情
                                               create_time TIMESTAMP -- 操作时间
);

CREATE TABLE IF NOT EXISTS system_config (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                                               config_key TEXT UNIQUE NOT NULL,
                                               config_value TEXT,
                                               updated_at TIMESTAMP
);

INSERT OR IGNORE INTO system_config(config_key, config_value) VALUES('wechat_webhook', 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=f3085410-ae83-4509-a71c-4b799c9631cc');
INSERT OR IGNORE INTO system_config(config_key, config_value) VALUES('log_wechat_webhook', 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=4c339bf6-62af-4f51-a308-dbff72062035');
INSERT OR IGNORE INTO system_config(config_key, config_value) VALUES('quiet_start', '20');
INSERT OR IGNORE INTO system_config(config_key, config_value) VALUES('quiet_end', '8');
INSERT OR IGNORE INTO system_config(config_key, config_value) VALUES('push_time', '09:30');
