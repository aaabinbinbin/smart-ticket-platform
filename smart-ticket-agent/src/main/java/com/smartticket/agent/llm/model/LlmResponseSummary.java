package com.smartticket.agent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 工具结果总结结构化输出。
 *
 * <p>模型只能基于 Tool 返回结果生成回复，不能补充 Tool 结果中不存在的事实。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponseSummary {
    /**
     * 面向用户的最终回复文本。
     */
    private String reply;
}
