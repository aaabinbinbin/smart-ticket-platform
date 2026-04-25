package com.smartticket.agent.orchestration;

import com.smartticket.agent.command.AgentCommandDraft;
import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 单轮执行结果摘要。
 *
 * <p>该对象位于 Agent 主链的结果收敛点，用于把当前分散在 AgentFacade 各分支中的 Tool 结果、
 * 模型输出、pendingAction 状态和最终回复统一为一个结构化模型。P1 阶段先用于统一回复渲染，
 * 后续阶段再逐步让 context/memory/trace 读取同一个 summary。
 * 该对象本身不执行写操作，也不会直接修改 session、memory、pendingAction 或 trace。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionSummary {
    /**
     * 本轮执行的最终状态。
     */
    private AgentTurnStatus status;

    /**
     * 当前结果来自哪一种执行路径。
     */
    private AgentExecutionMode mode;

    /**
     * 本轮识别出的业务意图。
     */
    private AgentIntent intent;

    /**
     * 当前执行链路使用的结构化参数。
     */
    private AgentToolParameters parameters;

    /**
     * 写操作路径生成的命令草稿。
     *
     * <p>P3 开始该字段用于表达 CREATE_TICKET、TRANSFER_TICKET 这类写链路在执行前已经识别出的
     * 命令语义，便于后续 trace、reply 和 pending 统一读取，而不用再从分散变量里回推。</p>
     */
    private AgentCommandDraft commandDraft;

    /**
     * 本轮主结果，通常来自 Tool 执行或 Guard 决策转换。
     */
    private AgentToolResult primaryResult;

    /**
     * 当前轮可能需要落库或继续恢复的 pendingAction。
     */
    private AgentPendingAction pendingAction;

    /**
     * 只读 ReAct 场景下的模型总结文本。
     *
     * <p>P1 阶段保留该字段是为了先不改变既有行为：当前 ReAct 路径仍会优先返回模型文本。
     * 后续阶段再继续把只读总结收敛为更可控的 observation/result 渲染。</p>
     */
    private String modelReply;

    /**
     * 最终渲染后的回复文本。
     */
    private String renderedReply;

    /**
     * 当前路径是否使用了 Spring AI。
     */
    private boolean springAiUsed;

    /**
     * 当前路径是否是确定性回退。
     */
    private boolean fallbackUsed;

    /**
     * 当前路径是否实际执行过 Tool。
     */
    private boolean toolInvoked;

    /**
     * 当前轮是否经过降级路径。
     *
     * <p>P6 高压治理用该字段把 LLM/RAG 不可用、预算收紧后的确定性处理显式暴露给 trace，
     * 避免仅凭 fallbackUsed 无法区分“正常确定性路径”和“压力下降级路径”。</p>
     */
    private boolean degraded;

    /**
     * 当前轮失败或降级时的工程错误码。
     */
    private String failureCode;

    /**
     * 当前轮失败或降级的可审计原因。
     */
    private String failureReason;

    /**
     * 判断当前 summary 是否含有主结果。
     *
     * @return true 表示已有 primaryResult，可供回复渲染和后续提交复用
     */
    public boolean hasPrimaryResult() {
        return primaryResult != null;
    }

    /**
     * 判断当前状态是否需要保留 pendingAction。
     *
     * @return true 表示本轮处于补参或确认阶段，后续还需要恢复该状态
     */
    public boolean needsPendingPersist() {
        return status == AgentTurnStatus.NEED_MORE_INFO
                || status == AgentTurnStatus.NEED_CONFIRMATION;
    }

    /**
     * 提取当前摘要中可供会话上下文使用的工单指针。
     *
     * @return 当前活跃工单 ID；无主结果时返回 null
     */
    public Long activeTicketId() {
        return primaryResult == null ? null : primaryResult.getActiveTicketId();
    }

    /**
     * 提取当前摘要中可供会话上下文使用的处理人指针。
     *
     * @return 当前活跃处理人 ID；无主结果时返回 null
     */
    public Long activeAssigneeId() {
        return primaryResult == null ? null : primaryResult.getActiveAssigneeId();
    }
}
