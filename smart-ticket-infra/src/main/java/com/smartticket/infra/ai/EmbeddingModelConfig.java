package com.smartticket.infra.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Embedding 模型配置骨架。
 *
 * <p>EmbeddingModel 由 Spring AI 的模型 starter 自动装配。本类只提供一个轻量 holder，
 * 方便后续 rag 模块在替换本地 hash embedding 时明确依赖 Spring AI Embedding 能力。
 * 文本切片、知识记录读取和向量写入流程仍然属于 rag 模块。</p>
 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingModelConfig {

    /**
     * 暴露 EmbeddingModel 持有对象。
     *
     * <p>只有在显式开启 smart-ticket.ai.embedding.enabled=true 且存在 EmbeddingModel
     * 时才创建，避免没有模型密钥时影响本地开发。</p>
     *
     * @param embeddingModel Spring AI 自动装配出的向量模型
     * @return 项目 Embedding 模型持有对象
     */
    @Bean
    @ConditionalOnMissingBean(SpringAiEmbeddingModelHolder.class)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnProperty(prefix = "smart-ticket.ai.embedding", name = "enabled", havingValue = "true")
    public SpringAiEmbeddingModelHolder springAiEmbeddingModelHolder(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingModelHolder(embeddingModel);
    }

    /**
     * Spring AI EmbeddingModel 的轻量持有对象。
     *
     * @param embeddingModel 向量模型实例
     */
    public record SpringAiEmbeddingModelHolder(EmbeddingModel embeddingModel) {
    }
}
