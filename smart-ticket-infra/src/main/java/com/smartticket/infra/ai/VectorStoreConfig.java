package com.smartticket.infra.ai;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * PGvector configuration that keeps MySQL as the primary business datasource
 * while exposing an independent PostgreSQL-backed VectorStore for RAG.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        VectorStoreConfig.PgVectorDataSourceProperties.class,
        PgVectorStoreProperties.class
})
public class VectorStoreConfig {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);


    @Bean(name = "pgvectorJdbcTemplate")
    @ConditionalOnMissingBean(name = "pgvectorJdbcTemplate")
    @ConditionalOnProperty(prefix = "smart-ticket.ai.vector-store", name = "enabled", havingValue = "true")
    public JdbcTemplate pgvectorJdbcTemplate(PgVectorDataSourceProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("smart-ticket.ai.pgvector.url is required when PGvector is enabled");
        }

        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName("org.postgresql.Driver")
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
        dataSource.setPoolName("PgVectorHikariPool");
        dataSource.setMaximumPoolSize(properties.getMaxPoolSize());
        dataSource.setMinimumIdle(properties.getMinIdle());
        dataSource.setConnectionTimeout(properties.getConnectionTimeoutMs());
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(SpringAiVectorStoreHolder.class)
    @ConditionalOnProperty(prefix = "smart-ticket.ai.vector-store", name = "enabled", havingValue = "true")
    public SpringAiVectorStoreHolder springAiVectorStoreHolder(
            JdbcTemplate pgvectorJdbcTemplate,
            EmbeddingModel embeddingModel,
            PgVectorStoreProperties properties
    ) {
        try {
            PgVectorStore vectorStore = PgVectorStore.builder(pgvectorJdbcTemplate, embeddingModel)
                    .schemaName(properties.getSchemaName())
                    .vectorTableName(properties.getTableName())
                    .dimensions(properties.getDimensions())
                    .distanceType(properties.getDistanceType())
                    .indexType(properties.getIndexType())
                    .idType(properties.getIdType())
                    .initializeSchema(properties.isInitializeSchema())
                    .removeExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable())
                    .vectorTableValidationsEnabled(properties.isSchemaValidation())
                    .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                    .build();
            vectorStore.afterPropertiesSet();
            return new SpringAiVectorStoreHolder(vectorStore);
        } catch (RuntimeException ex) {
            log.warn("PGvector VectorStore 初始化失败，将继续使用 MySQL fallback。reason={}", ex.getMessage());
            return new SpringAiVectorStoreHolder(null);
        } catch (Exception ex) {
            log.warn("PGvector VectorStore 初始化失败，将继续使用 MySQL fallback。reason={}", ex.getMessage());
            return new SpringAiVectorStoreHolder(null);
        }
    }

    public record SpringAiVectorStoreHolder(PgVectorStore vectorStore) {
    }

    @ConfigurationProperties(prefix = "smart-ticket.ai.pgvector")
    public static class PgVectorDataSourceProperties {
        private String url;
        private String username;
        private String password;
        private int maxPoolSize = 5;
        private int minIdle = 1;
        private long connectionTimeoutMs = 5000L;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }
}
