package com.smartticket.rag.security;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * RAG 敏感信息脱敏器。
 *
 * <p>该组件位于 embedding 入库前，用统一占位符替换手机号、邮箱、身份证、IP、JWT、
 * Bearer Token、password、secret 和 access key。脱敏后文本可进入 MySQL fallback 表和
 * PGvector VectorStore；该类不修改工单业务事实，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Component
public class SensitiveInfoMasker {
    private final SensitiveInfoDetector detector;

    /**
     * 构造敏感信息脱敏器。
     *
     * @param detector 敏感信息检测器
     */
    public SensitiveInfoMasker(SensitiveInfoDetector detector) {
        this.detector = detector;
    }

    /**
     * 对文本进行脱敏。
     *
     * @param text 原始文本
     * @return 脱敏后的文本；空文本原样返回
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text;
        for (Map.Entry<String, Pattern> entry : detector.patterns().entrySet()) {
            // 使用类型化占位符保留问题语义，同时避免原始凭证进入向量库和日志复盘链路。
            masked = entry.getValue().matcher(masked).replaceAll("[" + entry.getKey() + "_MASKED]");
        }
        return masked;
    }
}
