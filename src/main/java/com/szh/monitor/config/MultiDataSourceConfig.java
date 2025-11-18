package com.szh.monitor.config;

import com.szh.monitor.context.ExecuteJDBCContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MultiDataSourceConfig {

    @Value("${datasource.config.relation}")
    private String relation;

    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    public String getName(String code){
        String[] split = this.relation.split(",");
        for (String s : split) {
            String[] nameCode = s.split("-");
            if(nameCode[1].equals(code)){
                return nameCode[0];
            }
        }
        return "-";
    }


    @Bean("primaryDataSource")
    @Primary
    @ConfigurationProperties(prefix = "datasource.primary")
    public DataSource primaryDatasource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("secondaryDataSource")
    @ConfigurationProperties(prefix = "datasource.secondary")
    @ConditionalOnProperty(prefix = "datasource.secondary", name = "enabled", havingValue = "true")
    public DataSource secondaryDatasource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        executeJDBCContext.addJdbcTemplate(getName("primary"),"primaryJdbcTemplate");
        return jdbcTemplate;
    }

    @Bean("secondaryJdbcTemplate")
    @ConditionalOnProperty(prefix = "datasource.secondary", name = "enabled", havingValue = "true")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        executeJDBCContext.addJdbcTemplate(getName("secondary"),"secondaryJdbcTemplate");
        return jdbcTemplate;
    }

}
