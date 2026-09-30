package com.smartidc.framework.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 显式声明主业务 MySQL 数据源，防止被 PgVector 数据源抢占
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        // DataSourceProperties 会自动把 yml 中的 url 适配给 Hikari 的 jdbcUrl
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }
}