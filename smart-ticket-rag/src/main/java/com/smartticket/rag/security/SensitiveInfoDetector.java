package com.smartticket.rag.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * RAG 敏感信息检测器。
 *
 * <p>该组件位于知识入库前的安全边界，用于识别不应进入 embedding 和向量库的个人信息、
 * 访问凭证和密钥片段。它只做文本检测，不修改工单、知识、session、memory、pendingAction 或 trace。</p>
 */
@Component
public class SensitiveInfoDetector {
    private static final Map<String, Pattern> PATTERNS = buildPatterns();

    /**
     * 判断文本是否包含敏感信息。
     *
     * @param text 待检测文本
     * @return true 表示命中至少一类敏感信息
     */
    public boolean containsSensitiveInfo(String text) {
        return !detectTypes(text).isEmpty();
    }

    /**
     * 返回文本中命中的敏感信息类型。
     *
     * @param text 待检测文本
     * @return 命中的敏感类型编码，按规则定义顺序返回
     */
    public List<String> detectTypes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return PATTERNS.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(text).find())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 暴露检测规则给脱敏器复用。
     *
     * @return 敏感类型到正则规则的只读视图
     */
    Map<String, Pattern> patterns() {
        return PATTERNS;
    }

    private static Map<String, Pattern> buildPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        patterns.put("BEARER_TOKEN", Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*"));
        patterns.put("JWT", Pattern.compile("\\beyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"));
        patterns.put("ACCESS_KEY", Pattern.compile("(?i)\\b(access[_-]?key|ak)\\s*[:=]\\s*[A-Za-z0-9_\\-]{8,}"));
        patterns.put("PASSWORD", Pattern.compile("(?i)\\b(password|passwd|pwd)\\s*[:=]\\s*[^\\s,;]+"));
        patterns.put("SECRET", Pattern.compile("(?i)\\b(secret|client_secret|api_secret)\\s*[:=]\\s*[^\\s,;]+"));
        patterns.put("EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
        patterns.put("PHONE", Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"));
        patterns.put("ID_CARD", Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)"));
        patterns.put("IP", Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\b"));
        return Collections.unmodifiableMap(patterns);
    }
}
