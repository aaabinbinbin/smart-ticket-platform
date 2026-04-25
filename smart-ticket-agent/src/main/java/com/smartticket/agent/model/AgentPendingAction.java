package com.smartticket.agent.model;

import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体待办动作模型。
 *
 * <p>该模型保存补参或高风险确认在多轮对话之间需要恢复的最小状态，位于 Agent 主链的
 * PendingActionCoordinator 与 session context 之间。它本身不执行写操作，但一旦被恢复，
 * 可能引导后续确定性命令链路继续执行，因此必须记录过期时间以限制旧命令被误确认的风险。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingAction {
    // pending意图
    private AgentIntent pendingIntent;

    // pending工具Name
    private String pendingToolName;

    // pending参数
    private AgentToolParameters pendingParameters;

    @Builder.Default
    private List<AgentToolParameterField> awaitingFields = new ArrayList<>();

    // awaitingConfirmation
    private boolean awaitingConfirmation;

    // confirmation摘要
    private String confirmationSummary;

    // last工具结果
    private AgentToolResult lastToolResult;

    /**
     * pendingAction 创建时间。
     *
     * <p>该字段用于审计和过期判断，不参与 Tool 参数，也不会让 LLM 获得额外执行能力。</p>
     */
    private LocalDateTime createdAt;

    /**
     * pendingAction 过期时间。
     *
     * <p>过期后确认消息不能继续触发写操作，避免用户很久之后误确认旧的高风险命令。</p>
     */
    private LocalDateTime expiresAt;

    /**
     * 判断待办是否已经过期。
     *
     * @param now 当前时间
     * @return true 表示该 pendingAction 不能再继续执行
     */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now != null && !expiresAt.isAfter(now);
    }
}
