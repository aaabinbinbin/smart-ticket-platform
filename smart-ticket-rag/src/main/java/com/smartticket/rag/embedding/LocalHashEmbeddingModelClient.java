package com.smartticket.rag.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 本地可运行的轻量 embedding 适配器。
 *
 * <p>该实现使用文本哈希生成稳定向量，目的是先打通知识切片和向量入库链路。生产环境应替换为真实
 * embedding 模型客户端。</p>
 */
@Component
public class LocalHashEmbeddingModelClient implements EmbeddingModelClient {
    // DIMENSION
    private static final int VECTOR_DIMENSION = 32;

    /**
     * 生成向量。
     */
    @Override
    public List<Double> embed(String text) {
        byte[] digest = sha256(text == null ? "" : text);
        List<Double> vector = new ArrayList<>(VECTOR_DIMENSION);
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            int value = digest[i % digest.length] & 0xff;
            vector.add((value - 128) / 128.0d);
        }
        return vector;
    }

    /** 计算 SHA-256 摘要，作为本地稳定向量的种子。 */
    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
