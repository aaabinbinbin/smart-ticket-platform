package com.smartticket.agent.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent LLM 配置。
 *
 * <p>默认读取用户本地已有的 MY_API_KEY 和 MY_BASE_URL，模型名称可以后续通过
 * smart-ticket.agent.llm.model 或 AGENT_LLM_MODEL 覆盖。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "smart-ticket.agent.llm")
public class AgentLlmProperties {
    /**
     * 是否显式启用 LLM。
     *
     * <p>为 false 时强制关闭 LLM；为空或 true 时，还需要 apiKey、baseUrl、model 都有效。</p>
     */
    private Boolean enabled;

    /**
     * OpenAI 兼容接口地址。
     *
     * <p>支持形如 https://host/v1 或 https://host/v1/chat/completions 的配置。</p>
     */
    private String baseUrl = System.getenv("MY_BASE_URL");

    /**
     * LLM 调用密钥。不要写入代码仓库。
     */
    private String apiKey = System.getenv("MY_API_KEY");

    /**
     * 默认模型名。
     *
     * <p>本地可通过 AGENT_LLM_MODEL 或配置文件覆盖。</p>
     */
    private String model = defaultModel();

    /**
     * LLM HTTP 读取超时时间，单位毫秒。
     */
    private int timeoutMs = 30000;

    /**
     * 模型采样温度。阶段八以结构化输出为主，默认使用较低温度保证稳定。
     */
    private double temperature = 0.1;

    /**
     * 单次 Chat Completion 最大输出 token 数。
     */
    private int maxTokens = 800;

    /**
     * 判断当前 LLM 配置是否足以发起真实调用。
     */
    public boolean isEffectiveEnabled() {
        if (enabled != null && !enabled) {
            return false;
        }
        return hasText(apiKey) && hasText(baseUrl) && hasText(model);
    }

    /**
     * 解析默认模型名。优先使用环境变量，未配置时使用一个 OpenAI 兼容的轻量默认值。
     */
    private static String defaultModel() {
        String envModel = System.getenv("AGENT_LLM_MODEL");
        return hasText(envModel) ? envModel : "gpt-4o-mini";
    }

    /**
     * 本类内部使用的字符串非空判断，避免额外引入工具类依赖。
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
