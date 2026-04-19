package com.smartticket.agent.llm.model;

import com.smartticket.agent.model.AgentIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 意图识别结构化输出。
 *
 * <p>该对象只表示模型建议，进入 Tool 前还需要经过代码置信度和枚举校验。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmIntentDecision {
    /**
     * 模型判断出的业务意图，只允许映射到 AgentIntent 中已有的枚举。
     */
    private AgentIntent intent;

    /**
     * 模型对意图判断的置信度，取值范围期望为 0.0 到 1.0。
     */
    private Double confidence;

    /**
     * 模型给出的简短判断原因，仅用于日志和排查，不参与业务决策。
     */
    private String reason;
}
