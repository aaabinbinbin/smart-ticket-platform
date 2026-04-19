package com.smartticket.agent.llm.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat Completion 消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmMessage {
    /**
     * 消息角色，例如 system、user、assistant。
     */
    private String role;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 构造 system 消息，用于放置全局规则和 Prompt 边界。
     */
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    /**
     * 构造 user 消息，用于放置用户输入、上下文和工具结果等动态载荷。
     */
    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }
}
