CREATE TABLE IF NOT EXISTS operation_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type VARCHAR(50) NOT NULL,
    operation_module VARCHAR(100) NOT NULL,
    operation_content TEXT,
    target_id VARCHAR(100),
    ip_address VARCHAR(50),
    browser_info VARCHAR(200),
    user_agent TEXT,
    environment_name VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sql_data_source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_name VARCHAR(100) NOT NULL UNIQUE,
    jdbc_url TEXT NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(200) NOT NULL,
    driver_class_name VARCHAR(200),
    enabled BOOLEAN DEFAULT 1,
    maximum_pool_size INTEGER DEFAULT 20,
    minimum_idle INTEGER DEFAULT 5,
    max_lifetime INTEGER DEFAULT 1800000,
    idle_timeout INTEGER DEFAULT 600000,
    connection_timeout INTEGER DEFAULT 30000,
    keepalive_time INTEGER DEFAULT 0,
    connection_test_query VARCHAR(100),
    validation_timeout INTEGER DEFAULT 5000,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS remote_log_source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    environment_name VARCHAR(100) NOT NULL UNIQUE,
    url TEXT NOT NULL,
    datasource_id VARCHAR(100),
    username VARCHAR(100),
    password VARCHAR(200),
    webhook TEXT,
    week VARCHAR(50),
    start_time TIME,
    end_time TIME,
    monitors TEXT,
    enabled BOOLEAN DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);