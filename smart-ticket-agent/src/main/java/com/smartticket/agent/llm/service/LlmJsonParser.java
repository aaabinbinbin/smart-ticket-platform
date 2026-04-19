package com.smartticket.agent.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * LLM JSON 输出解析器。
 *
 * <p>模型可能返回 Markdown 包裹或额外说明，本解析器会尽量截取第一个 JSON 对象再解析。</p>
 */
@Component
public class LlmJsonParser {
    /**
     * Jackson JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    public LlmJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 LLM 输出解析为指定结构化对象。
     *
     * @param content 模型原始输出
     * @param type 目标结构化类型
     * @return 解析后的结构化对象
     */
    public <T> T parse(String content, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(extractJson(content), type);
    }

    /**
     * 从模型输出中截取 JSON 对象。
     *
     * <p>这里采用最小兜底策略，只截取首个左大括号到最后一个右大括号之间的内容。</p>
     */
    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        String text = content.trim();
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        return text;
    }
}
