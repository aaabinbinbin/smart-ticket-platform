package com.smartticket.biz.service.ticket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM 工单字段抽取器。
 *
 * <p>调用 LLM 从 title + description 中抽取结构化字段（type/category/priority/typeProfile）。
 * LLM 只负责字段抽取，不决定是否创建工单。调用失败或结果不合法时返回 null，
 * 由调用方降级到规则 enrichment。</p>
 */
public class TicketCreateLlmEnricher {

    private static final Logger log = LoggerFactory.getLogger(TicketCreateLlmEnricher.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String SYSTEM_PROMPT = """
            你是智能工单系统的字段抽取器。
            请根据用户的标题和描述，抽取工单类型、分类、优先级和类型画像。

            工单类型(type)必须是以下之一：INCIDENT, ACCESS_REQUEST, CHANGE_REQUEST, ENVIRONMENT_REQUEST, CONSULTATION
            分类(category)必须是以下之一：ACCOUNT, SYSTEM, ENVIRONMENT, OTHER
            优先级(priority)必须是以下之一：LOW, MEDIUM, HIGH, URGENT

            类型画像(typeProfile)必须包含对应类型要求的字段：
            - INCIDENT: symptom(故障现象), impactScope(影响范围)
            - ACCESS_REQUEST: accountId(账号标识), targetResource(目标资源), requestedRole(申请角色), justification(申请原因)
            - CHANGE_REQUEST: changeTarget(变更对象), changeWindow(变更窗口), rollbackPlan(回滚方案), impactScope(影响范围)
            - ENVIRONMENT_REQUEST: environmentName(环境名称), resourceSpec(资源规格), purpose(用途说明)
            - CONSULTATION: questionTopic(咨询主题), expectedOutcome(期望结果)

            只返回 JSON，不要返回 Markdown，不要解释。
            如果信息不足，字段值填"待确认"。
            不要编造用户没有提供的具体账号、资源、时间窗口。
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            标题：%s
            描述：%s
            请返回 JSON 格式（不要 Markdown 代码块包裹）：
            {
              "type": "INCIDENT",
              "category": "SYSTEM",
              "priority": "MEDIUM",
              "typeProfile": {
                "symptom": "...",
                "impactScope": "..."
              },
              "confidence": 0.85,
              "reason": "简要说明推断理由"
            }
            """;

    private final ChatClient chatClient;
    private final TicketCreateEnrichmentProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 构造 LLM 字段抽取器。
     */
    public TicketCreateLlmEnricher(
            ChatClient chatClient,
            TicketCreateEnrichmentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 LLM 抽取工单字段。
     *
     * @return LLM 抽取结果，失败或不可用时返回 null
     */
    public LlmEnrichmentResult enrich(String title, String description) {
        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
                title == null ? "" : title,
                description == null ? "" : description
        );
        try {
            String responseText = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            if (responseText == null || responseText.isBlank()) {
                log.warn("LLM enrichment 返回空内容");
                return null;
            }
            return parseAndValidate(responseText);
        } catch (Exception ex) {
            log.warn("LLM enrichment 调用异常：{}", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析并校验 LLM 返回的 JSON。
     */
    LlmEnrichmentResult parseAndValidate(String jsonText) {
        String clean = jsonText.trim();
        // 去除可能的 Markdown 代码块包裹
        if (clean.startsWith("```")) {
            int start = clean.indexOf('\n');
            int end = clean.lastIndexOf("```");
            if (start > 0 && end > start) {
                clean = clean.substring(start, end).trim();
            }
        }

        Map<String, Object> map;
        try {
            map = objectMapper.readValue(clean, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("LLM 返回结果 JSON 解析失败");
            return null;
        }

        if (map == null || map.isEmpty()) {
            log.warn("LLM 返回空 JSON");
            return null;
        }

        // 解析 type
        TicketTypeEnum type = parseEnum(map, "type", TicketTypeEnum.class);
        // 解析 category
        TicketCategoryEnum category = parseEnum(map, "category", TicketCategoryEnum.class);
        // 解析 priority
        TicketPriorityEnum priority = parseEnum(map, "priority", TicketPriorityEnum.class);

        // 解析 typeProfile
        Map<String, Object> typeProfile = parseTypeProfile(map);

        // 解析 confidence
        double confidence = parseConfidence(map);

        // 解析 reason
        String reason = asString(map.get("reason"));

        return new LlmEnrichmentResult(type, category, priority, typeProfile, confidence, reason);
    }

    // ========== 校验辅助方法 ==========

    /**
     * 解析枚举值，如果无效则返回 null。
     */
    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> T parseEnum(Map<String, Object> map, String key, Class<T> enumClass) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim().toUpperCase();
        if (str.isEmpty()) {
            return null;
        }
        try {
            return (T) Enum.valueOf(enumClass, str);
        } catch (IllegalArgumentException ex) {
            log.warn("LLM 返回的 {}={} 不在枚举 {} 中，丢弃该字段", key, str, enumClass.getSimpleName());
            return null;
        }
    }

    /**
     * 解析 typeProfile。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTypeProfile(Map<String, Object> map) {
        Object profile = map.get("typeProfile");
        if (profile instanceof Map<?, ?> profileMap) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : profileMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String value = String.valueOf(entry.getValue()).trim();
                    if (!value.isEmpty() && value.length() < 1000) {
                        result.put(String.valueOf(entry.getKey()), value);
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 解析 confidence。
     */
    private double parseConfidence(Map<String, Object> map) {
        Object conf = map.get("confidence");
        if (conf instanceof Number num) {
            return num.doubleValue();
        }
        if (conf instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * 安全转 String。
     */
    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }

    // ========== 内部结果类 ==========

    /**
     * LLM 抽取结果。
     */
    public record LlmEnrichmentResult(
            TicketTypeEnum type,
            TicketCategoryEnum category,
            TicketPriorityEnum priority,
            Map<String, Object> typeProfile,
            double confidence,
            String reason
    ) {

        /**
         * LLM 结果是否可用（type/category/priority 至少有一个有效，且置信度达标）。
         */
        public boolean isUsable(double minConfidence) {
            return confidence >= minConfidence
                    && (type != null || category != null || priority != null || typeProfile != null);
        }
    }
}
