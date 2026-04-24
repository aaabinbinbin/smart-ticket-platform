package com.smartticket.agent.router;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.prompt.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 LLM 的意图分类器。当 Spring AI ChatClient 可用时调用大模型做意图分类，
 * 不可用时返回 null，由调用方（IntentRouter）回退到关键词匹配。
 *
 * <p>分类提示词从 classpath:agent/prompt/intent-classification.md 加载，
 * 支持不重启修改 prompt。</p>
 */
@Component
public class LlmIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmIntentClassifier.class);
    private static final String PROMPT_CODE = "intent-classification";

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final boolean chatEnabled;
    private final PromptTemplateService promptTemplateService;

    public LlmIntentClassifier(
            ObjectProvider<ChatClient> chatClientProvider,
            @Value("${smart-ticket.ai.chat.enabled:false}") boolean chatEnabled,
            PromptTemplateService promptTemplateService
    ) {
        this.chatClientProvider = chatClientProvider;
        this.chatEnabled = chatEnabled;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 使用 LLM 对用户消息做意图分类。LLM 不可用时返回 null，
     * 由调用方走关键词路由兜底。
     */
    public IntentRoute classify(String message) {
        if (!chatEnabled) {
            return null;
        }
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return null;
        }

        try {
            String template = promptTemplateService.content(PROMPT_CODE,
                    "你是一个工单系统的意图分类器。用户输入：「%s」。请判断意图：QUERY_TICKET、CREATE_TICKET、TRANSFER_TICKET、SEARCH_HISTORY。输出 JSON。");
            String prompt = template.formatted(message);

            String response = chatClient.prompt()
                    .system("你是一个意图分类助手，只输出 JSON，不要多余内容。")
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return null;
            }

            return parseResponse(response);
        } catch (RuntimeException ex) {
            log.warn("LLM 意图分类失败，将使用关键词兜底。reason={}", ex.getMessage());
            return null;
        }
    }

    private IntentRoute parseResponse(String response) {
        try {
            // 从 LLM 返回内容中提取 JSON 对象
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            String json = response.substring(start, end + 1);

            String intent = extractJsonString(json, "intent");
            double confidence = extractJsonDouble(json, "confidence");
            String reason = extractJsonString(json, "reason");

            if (intent == null || confidence <= 0) {
                return null;
            }

            AgentIntent agentIntent;
            try {
                agentIntent = AgentIntent.valueOf(intent.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }

            return IntentRoute.builder()
                    .intent(agentIntent)
                    .confidence(Math.min(confidence, 0.98))
                    .reason("[LLM] " + (reason != null ? reason : "LLM 分类结果"))
                    .build();
        } catch (RuntimeException ex) {
            log.warn("解析 LLM 意图响应失败: {}", response);
            return null;
        }
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;

        // 跳过空格
        int contentStart = colonIdx + 1;
        while (contentStart < json.length() && json.charAt(contentStart) == ' ') {
            contentStart++;
        }

        if (contentStart >= json.length()) return null;

        if (json.charAt(contentStart) == '"') {
            int quoteEnd = contentStart + 1;
            while (quoteEnd < json.length() && json.charAt(quoteEnd) != '"') {
                if (json.charAt(quoteEnd) == '\\') quoteEnd++;
                quoteEnd++;
            }
            return json.substring(contentStart + 1, quoteEnd);
        }
        return null;
    }

    private double extractJsonDouble(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return 0;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return 0;
        int numStart = colonIdx + 1;
        while (numStart < json.length() && json.charAt(numStart) == ' ') numStart++;
        int numEnd = numStart;
        while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd))
                || json.charAt(numEnd) == '.' || json.charAt(numEnd) == '-')) {
            numEnd++;
        }
        if (numEnd <= numStart) return 0;
        try {
            return Double.parseDouble(json.substring(numStart, numEnd));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
