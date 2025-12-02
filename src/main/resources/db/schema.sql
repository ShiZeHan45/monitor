CREATE TABLE IF NOT EXISTS sql_execute_log (
                                               id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                               environment_name TEXT, -- 环境名称
                                               sql_file_name TEXT, -- SQL文件名称
                                               execute_date INTEGER, -- 执行日期
                                               failed_count INTEGER, -- 失败次数
                                               failed_count_reset_time INTEGER, -- 失败次数重置时间
                                               count INTEGER -- 执行次数
);

CREATE TABLE IF NOT EXISTS msg_send_log(
                                           id INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
                                           content TEXT, -- 推送内容
                                           send_webhook TEXT, -- 推送地址
                                           msg_type TEXT, -- 消息类型
                                           create_time TIMESTAMP , -- 内容产生时间
                                           send_date TIMESTAMP , -- 内容推送日期
                                           send_status INTEGER -- 已发送 1发送 0未发送
);
