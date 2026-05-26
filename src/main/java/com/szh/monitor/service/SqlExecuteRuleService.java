package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.SqlExecuteRule;

import java.util.List;

public interface SqlExecuteRuleService extends IService<SqlExecuteRule> {

    List<SqlExecuteRule> findByEnvironmentName(String environmentName);

}
