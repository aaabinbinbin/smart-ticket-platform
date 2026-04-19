package com.smartticket.agent.llm.client;

import com.smartticket.agent.llm.config.AgentLlmProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 兼容 Chat Completion 适配器。
 *
 * <p>本阶段只实现最小可用 HTTP 适配，后续如果切换 Spring AI，可保持 LlmClient 接口不变。</p>
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    /**
     * LLM 调用配置，包含 baseUrl、apiKey、model、超时和采样参数。
     */
    private final AgentLlmProperties properties;

    /**
     * Spring Web 提供的轻量 HTTP 客户端。
     */
    private final RestClient restClient;

    public OpenAiCompatibleLlmClient(AgentLlmProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 只有配置完整且未被显式关闭时，才允许真实调用模型。
     */
    @Override
    public boolean isAvailable() {
        return properties.isEffectiveEnabled();
    }

    /**
     * 调用 OpenAI 兼容的 /chat/completions 接口。
     *
     * <p>该方法只负责取回模型文本，不负责解释业务语义；业务解析和校验在 LlmAgentService 中完成。</p>
     */
    @Override
    public String complete(List<LlmMessage> messages) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM 配置未启用或不完整");
        }
        Map<String, Object> request = Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxTokens()
        );
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri(chatCompletionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new IllegalStateException("LLM 返回为空");
            }
            OpenAiChatChoice choice = response.choices().get(0);
            if (choice == null || choice.message() == null || choice.message().content() == null) {
                throw new IllegalStateException("LLM 消息为空");
            }
            return choice.message().content();
        } catch (RuntimeException ex) {
            log.warn("LLM 调用失败，将由上层降级处理: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * 兼容两类 baseUrl：
     *
     * <p>一种是网关根路径，例如 https://host/v1；另一种是完整 Chat Completion 地址。</p>
     */
    private String chatCompletionsUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "chat/completions";
        }
        return baseUrl + "/chat/completions";
    }

    /**
     * OpenAI 兼容响应的顶层结构。阶段八只关心 choices。
     */
    private record OpenAiChatResponse(List<OpenAiChatChoice> choices) {
    }

    /**
     * OpenAI 兼容响应中的候选回复。
     */
    private record OpenAiChatChoice(OpenAiChatMessage message) {
    }

    /**
     * OpenAI 兼容响应中的消息体。
     */
    private record OpenAiChatMessage(String role, String content) {
    }
}
