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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

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

    @Bean("sqliteDataSourceInitializer")
    public DataSourceInitializer dataSourceInitializer(@Qualifier("sqliteDataSource") DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        // 执行建表脚本
        populator.addScript(new ClassPathResource("db/schema.sql"));
        // 执行数据初始化脚本
        populator.addScript(new ClassPathResource("db/datainit.sql"));
        // 忽略脚本执行错误（比如表已经存在）
        populator.setContinueOnError(true);

        initializer.setDatabasePopulator(populator);
        return initializer;
    }
    @Bean(name = "sqliteSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqliteSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

}

