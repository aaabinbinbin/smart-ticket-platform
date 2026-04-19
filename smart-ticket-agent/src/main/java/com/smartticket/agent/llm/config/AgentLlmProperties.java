package com.smartticket.agent.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent LLM 启停配置。
 *
 * <p>模型供应商、base-url、api-key、model 等底层参数交给 Spring AI 的
 * spring.ai.openai.* 配置管理。本类只保留 Agent 是否允许调用模型的业务开关，
 * 避免 Agent 层继续维护一套 OpenAI HTTP 配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "smart-ticket.ai.chat")
public class AgentLlmProperties {
    /**
     * 是否允许 Agent 调用真实模型。
     *
     * <p>默认为 false，保证本地没有模型密钥时仍然可以通过规则 fallback 启动和调试。</p>
     */
    private boolean enabled = false;

    /**
     * 判断 Agent 是否允许调用真实模型。
     *
     * @return 开关开启时返回 true
     */
    public boolean isEffectiveEnabled() {
        return enabled;
    }
}
