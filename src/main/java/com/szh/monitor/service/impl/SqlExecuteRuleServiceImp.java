package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.SqlExecuteRule;
import com.szh.monitor.mapper.SqlExecuteRuleMapper;
import com.szh.monitor.service.SqlExecuteRuleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SqlExecuteRuleServiceImp extends ServiceImpl<SqlExecuteRuleMapper, SqlExecuteRule> implements SqlExecuteRuleService {


    @Override
    public List<SqlExecuteRule> findByEnvironmentName(String environmentName) {
        return getBaseMapper().findByEnvironmentName(environmentName);
    }
}
