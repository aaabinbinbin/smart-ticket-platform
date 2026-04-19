package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经过代码归一化后的工具调用计划。
 *
 * <p>该对象位于编排层，和 LLM 原始输出分离。只有通过编排器校验后的计划才会进入 Tool。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallPlan {
    /**
     * 本次计划对应的业务意图。
     */
    private AgentIntent intent;

    /**
     * 本次计划准备调用的 Tool 名称。
     */
    private String toolName;

    /**
     * 本次计划携带的结构化参数。
     */
    private AgentToolParameters parameters;

    /**
     * 是否来自 LLM 生成。false 表示来自阶段八 fallback。
     */
    private boolean llmGenerated;

    /**
     * 计划理由，用于日志排查。
     */
    private String reason;
}
