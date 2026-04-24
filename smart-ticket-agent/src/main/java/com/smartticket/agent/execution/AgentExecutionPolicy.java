package com.smartticket.agent.execution;

import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 单轮执行策略。
 *
 * <p>该对象位于意图路由与具体执行器之间，用于显式表达本轮允许的执行模式、工具暴露范围、
 * 是否允许 ReAct、是否允许自动执行以及预算上限。它本身不执行写操作，也不会直接修改
 * session、memory、pendingAction 或 trace。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionPolicy {
    /**
     * 本轮主链应采用的执行模式。
     */
    private AgentExecutionMode mode;

    /**
     * 当前策略允许使用的技能列表。
     */
    @Builder.Default
    private List<AgentSkill> allowedSkills = new ArrayList<>();

    /**
     * 当前策略允许暴露给执行层的工具名称。
     */
    @Builder.Default
    private List<String> allowedToolNames = new ArrayList<>();

    /**
     * 当前策略是否允许只读 ReAct。
     */
    private boolean allowReact;

    /**
     * 当前策略是否允许在无需确认时自动执行。
     */
    private boolean allowAutoExecute;

    /**
     * 当前策略是否要求执行前二次确认。
     */
    private boolean requireConfirmation;

    /**
     * 当前策略允许进入执行链的最高风险等级。
     */
    private ToolRiskLevel maxRiskLevel;

    /**
     * 当前轮建议超时时间。
     */
    private Duration timeout;

    /**
     * 当前轮建议最大 LLM 调用次数。
     */
    private int maxLlmCalls;

    /**
     * 当前轮建议最大 Tool 调用次数。
     */
    private int maxToolCalls;

    /**
     * 当前轮建议最大 RAG 调用次数。
     */
    private int maxRagCalls;

    /**
     * 判断当前策略是否允许暴露指定工具。
     *
     * @param toolName 工具名称
     * @return true 表示该工具在当前轮白名单内
     */
    public boolean allowsTool(String toolName) {
        return toolName != null && allowedToolNames != null && allowedToolNames.contains(toolName);
    }
}
