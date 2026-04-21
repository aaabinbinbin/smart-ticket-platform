package com.smartticket.rag.embedding;

import com.smartticket.infra.ai.EmbeddingModelConfig.SpringAiEmbeddingModelHolder;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI EmbeddingModel 的向量模型适配器。
 *
 * <p>该类是 RAG 模块访问 embedding 能力的主入口。生产环境启用
 * {@code smart-ticket.ai.embedding.enabled=true} 后优先调用 Spring AI；
 * 本地开发或模型不可用时退回稳定 hash 向量，保证知识构建链路可运行。</p>
 */
@Primary
@Component
public class SpringAiEmbeddingModelClient implements EmbeddingModelClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingModelClient.class);

    /** Spring AI EmbeddingModel 持有对象，只有显式启用配置时才会存在。 */
    private final ObjectProvider<SpringAiEmbeddingModelHolder> embeddingModelHolderProvider;

    /** 本地稳定向量兜底实现，用于无模型密钥和单元测试场景。 */
    private final LocalHashEmbeddingModelClient fallbackClient;

    public SpringAiEmbeddingModelClient(ObjectProvider<SpringAiEmbeddingModelHolder> embeddingModelHolderProvider) {
        this.embeddingModelHolderProvider = embeddingModelHolderProvider;
        this.fallbackClient = new LocalHashEmbeddingModelClient();
    }

    /**
     * 对文本生成向量。
     *
     * @param text 待向量化文本
     * @return 浮点向量列表
     */
    @Override
    public List<Double> embed(String text) {
        SpringAiEmbeddingModelHolder holder = embeddingModelHolderProvider.getIfAvailable();
        if (holder == null || holder.embeddingModel() == null) {
            return fallbackClient.embed(text);
        }
        try {
            return toDoubleList(holder.embeddingModel().embed(text == null ? "" : text));
        } catch (RuntimeException ex) {
            log.warn("spring ai embedding failed, fallback to local hash embedding: reason={}", ex.getMessage());
            return fallbackClient.embed(text);
        }
    }

    /** 将 Spring AI 返回的 float 数组转换为项目内部使用的 Double 列表。 */
    private List<Double> toDoubleList(float[] vector) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        List<Double> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add((double) value);
        }
        return result;
    }
}
