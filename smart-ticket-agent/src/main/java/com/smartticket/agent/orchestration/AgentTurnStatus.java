package com.smartticket.agent.orchestration;

/**
 * Agent 单轮执行状态。
 *
 * <p>该枚举位于 Agent 主链的结果层，用来统一描述一轮对话最终落到哪一种业务状态，
 * 便于后续把回复渲染、状态提交和 trace 收敛到同一个结构化结果上。
 * 该枚举本身不执行任何写操作，也不会直接修改 session、memory、pendingAction 或 trace。</p>
 */
public enum AgentTurnStatus {
    /**
     * 本轮已经完成，通常意味着 Tool 执行成功或查询结果可直接返回。
     */
    COMPLETED,

    /**
     * 当前还缺少必要信息，需要用户继续补充。
     */
    NEED_MORE_INFO,

    /**
     * 当前是高风险操作，必须等待用户确认后才能继续执行。
     */
    NEED_CONFIRMATION,

    /**
     * 用户显式取消了上一轮待处理动作。
     */
    CANCELLED,

    /**
     * 当前通过降级路径完成了本轮处理。
     */
    DEGRADED,

    /**
     * 本轮处理失败，无法继续完成当前请求。
     */
    FAILED
}
