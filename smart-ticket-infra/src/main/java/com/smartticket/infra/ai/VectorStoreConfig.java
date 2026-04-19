package com.smartticket.infra.ai;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI PGvector 向量库配置骨架。
 *
 * <p>PGvector 的 VectorStore 由 Spring AI starter 根据 spring.ai.vectorstore.pgvector.*
 * 配置自动装配。本类不直接创建 PgVectorStore，避免当前 MySQL 主数据源和后续 PostgreSQL
 * 向量库连接混在一起。后续启用 PGvector 时，应先补齐 PostgreSQL/pgvector 数据源方案，
 * 再让 rag 模块通过 VectorStore 执行相似度检索。</p>
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

    /**
     * 暴露 VectorStore 持有对象。
     *
     * <p>只有在显式开启 smart-ticket.ai.vector-store.enabled=true 且 Spring AI 已经
     * 创建 VectorStore 时才注册。RAG 模块后续可依赖该 holder 做迁移过渡。</p>
     *
     * @param vectorStore Spring AI 自动装配出的向量库
     * @return 项目向量库持有对象
     */
    @Bean
    @ConditionalOnMissingBean(SpringAiVectorStoreHolder.class)
    @ConditionalOnBean(VectorStore.class)
    @ConditionalOnProperty(prefix = "smart-ticket.ai.vector-store", name = "enabled", havingValue = "true")
    public SpringAiVectorStoreHolder springAiVectorStoreHolder(VectorStore vectorStore) {
        return new SpringAiVectorStoreHolder(vectorStore);
    }

    /**
     * Spring AI VectorStore 的轻量持有对象。
     *
     * @param vectorStore PGvector 向量库实例
     */
    public record SpringAiVectorStoreHolder(VectorStore vectorStore) {
    }
}
