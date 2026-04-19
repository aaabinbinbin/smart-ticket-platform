package com.smartticket.agent.llm.client;

import com.smartticket.agent.llm.config.AgentLlmProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI ChatClient 的 LLM 客户端。
 *
 * <p>该类替换原先手写的 OpenAI-compatible HTTP 客户端，避免模型调用层双栈并存。
 * 它只负责把 Agent 已经构造好的 system/user 消息交给 Spring AI，不解释业务语义，
 * 也不直接执行任何 Tool。Tool 计划、参数校验和业务执行仍由 Agent 编排层与 biz 层负责。</p>
 */
@Component
public class SpringAiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    /**
     * Agent 对模型调用的启停配置。
     */
    private final AgentLlmProperties properties;

    /**
     * Spring AI ChatClient 提供者。
     *
     * <p>使用 ObjectProvider 是为了在未配置模型密钥或未启用 ChatClient 时仍能正常启动应用，
     * 上层会自动回退到规则链路。</p>
     */
    private final ObjectProvider<ChatClient> chatClientProvider;

    public SpringAiLlmClient(AgentLlmProperties properties, ObjectProvider<ChatClient> chatClientProvider) {
        this.properties = properties;
        this.chatClientProvider = chatClientProvider;
    }

    /**
     * 判断当前是否具备真实模型调用条件。
     *
     * @return 已启用并且容器中存在 ChatClient 时返回 true
     */
    @Override
    public boolean isAvailable() {
        return properties.isEffectiveEnabled() && chatClientProvider.getIfAvailable() != null;
    }

    /**
     * 使用 Spring AI ChatClient 发起一次对话调用。
     *
     * @param messages Agent 已经构造好的 system/user 消息
     * @return 模型返回的文本内容
     */
    @Override
    public String complete(List<LlmMessage> messages) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (!properties.isEffectiveEnabled() || chatClient == null) {
            throw new IllegalStateException("Spring AI ChatClient 未启用或未完成配置");
        }
        String systemPrompt = collectByRole(messages, "system");
        String userPrompt = collectByRole(messages, "user");
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalStateException("Spring AI 返回内容为空");
            }
            return content;
        } catch (RuntimeException ex) {
            log.warn("Spring AI 模型调用失败，将由上层回退处理: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * 按角色合并消息内容。
     *
     * <p>当前 Agent 的 prompt 构造以 system + user 为主，合并后交给 ChatClient 可以保持
     * 现有编排逻辑不变。后续接入 Spring AI 原生多消息 API 时，可在这里集中调整。</p>
     */
    private String collectByRole(List<LlmMessage> messages, String role) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(message -> role.equals(message.getRole()))
                .map(LlmMessage::getContent)
                .filter(content -> content != null && !content.trim().isEmpty())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}
