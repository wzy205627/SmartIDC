package com.smartidc.aiops.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 智维云 AIOps - pgvector 专属数据源与 PgVectorStore 向量库装配配置
 * 避免与主业务 MySQL 数据源发生冲突
 */
@Configuration
public class PgVectorConfig {

    @Bean
    @ConfigurationProperties(prefix = "smartidc.aiops.pgvector.datasource")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("pgVectorDataSource")
    public DataSource pgVectorDataSource(DataSourceProperties pgVectorDataSourceProperties) {
        return pgVectorDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }

    @Bean
    @ConditionalOnMissingBean(PgVectorStore.class)
    public PgVectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgVectorJdbcTemplate,
            EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .vectorTableName("vector_store")
                .build();
    }
}
