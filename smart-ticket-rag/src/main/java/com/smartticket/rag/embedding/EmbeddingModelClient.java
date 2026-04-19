package com.smartticket.rag.embedding;

import java.util.List;

/**
 * Embedding 模型客户端抽象。
 *
 * <p>第一版先通过接口隔离模型调用方式，后续可以替换为 OpenAI 兼容 embedding API 或本地向量模型。</p>
 */
public interface EmbeddingModelClient {
    /**
     * 对文本生成向量。
     *
     * @param text 待向量化文本
     * @return 向量浮点数列表
     */
    List<Double> embed(String text);
}
