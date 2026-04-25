package com.smartticket.infra.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DashScope (阿里云通义千问) Native Embedding 模型适配器。
 *
 * <p>调用 DashScope 原生 text-embedding API（非 OpenAI 兼容模式），
 * 因为 {@code /compatible-mode/v1/embeddings} 端点不被 DashScope 支持（返回 404）。
 * Spring AI 的 OpenAiEmbeddingModel 在 DashScope 下不可用，本类替代它。</p>
 *
 * <p>当 API 调用失败时自动降级为确定性哈希向量（1536 维），
 * 确保知识构建和向量写入链路不被阻断。</p>
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "smart-ticket.ai.embedding", name = "enabled", havingValue = "true")
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingModel.class);

    /** 哈希回退时的向量维度，必须与 PGvector 表定义一致。 */
    private static final int FALLBACK_DIMENSION = 1536;

    /** DashScope 原生 embedding API 端点。 */
    private static final String DASHSCOPE_API_BASE = "https://dashscope.aliyuncs.com";
    private static final String EMBEDDING_PATH = "/api/v1/services/embeddings/text-embedding/text-embedding";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public DashScopeEmbeddingModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-v4}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions:1536}") int dimensions
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.restClient = RestClient.builder()
                .baseUrl(DASHSCOPE_API_BASE)
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i) == null ? "" : texts.get(i);
            float[] vector;
            try {
                vector = callDashScopeApi(text);
            } catch (RuntimeException ex) {
                log.warn("DashScope embedding API 调用失败，使用本地哈希回退。reason={}", ex.getMessage());
                vector = hashFallback(text);
            }
            embeddings.add(new Embedding(vector, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document == null ? "" : document.getText());
    }

    /**
     * 调用 DashScope 原生文本嵌入 API。
     *
     * <p>API 文档：https://help.aliyun.com/zh/model-studio/developer-reference/use-text-embedding-v4</p>
     */
    @SuppressWarnings("unchecked")
    private float[] callDashScopeApi(String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        // DashScope text-embedding API 要求 input 为 {"texts": ["..."]} 格式
        Map<String, Object> input = new HashMap<>();
        input.put("texts", List.of(text));
        requestBody.put("input", input);
        Map<String, Object> params = new HashMap<>();
        params.put("dimension", dimensions);
        requestBody.put("parameters", params);

        Map<String, Object> response = restClient.post()
                .uri(EMBEDDING_PATH)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("DashScope embedding API 返回空响应");
        }

        Map<String, Object> output = (Map<String, Object>) response.get("output");
        if (output == null) {
            throw new IllegalStateException("DashScope embedding API 响应缺少 output 字段");
        }

        List<Map<String, Object>> embeddings =
                (List<Map<String, Object>>) output.get("embeddings");
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("DashScope embedding API 响应中没有 embeddings");
        }

        List<Double> embedding = (List<Double>) embeddings.get(0).get("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("DashScope embedding 结果为空");
        }

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    /** 基于 SHA-256 的确定性哈希向量回退。 */
    private float[] hashFallback(String text) {
        byte[] digest = sha256(text);
        float[] vector = new float[FALLBACK_DIMENSION];
        for (int i = 0; i < FALLBACK_DIMENSION; i++) {
            int value = digest[i % digest.length] & 0xff;
            vector[i] = (value - 128) / 128.0f;
        }
        return vector;
    }

    /** float[] -> List<Float> 转换。 */
    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
