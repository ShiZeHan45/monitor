package com.szh.monitor.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = "com.szh.monitor.mapper",
        sqlSessionFactoryRef = "sqliteSqlSessionFactory"
)
public class SQLiteDataSourceConfig {

    @Bean(name = "sqliteDataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource sqliteDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "sqliteSqlSessionFactory")
    public SqlSessionFactory sqliteSqlSessionFactory(@Qualifier("sqliteDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml")
        );
        return bean.getObject();
    }

    @Bean(name = "sqliteSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqliteSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

}

